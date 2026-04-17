package com.main.accord.domain.account;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.main.accord.domain.dm.ParticipantRepository;
import com.main.accord.domain.server.MemberRepository;
import com.main.accord.websocket.ChatHandler;
import com.main.accord.websocket.PresenceBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceService implements PresenceBroadcastService {

    private final AccountRepository accountRepository;
    private final MemberRepository memberRepository;
    private final ParticipantRepository participantRepository;
    private final ChatHandler chatHandler;

    // In-memory presence cache (primary source of truth)
    private final Cache<UUID, PresenceStatus> presenceCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS) // Auto-cleanup if session dies without disconnect
            .maximumSize(10_000)
            .build();

    // Track last broadcast time to prevent spam
    private final Map<UUID, Long> lastBroadcastTime = new ConcurrentHashMap<>();
    private static final long MIN_BROADCAST_INTERVAL_MS = 1000; // 1 second

    // Track last set presence (manual user preference)
    private final Cache<UUID, PresenceStatus> lastSetPresenceCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .build();

    /**
     * Get current presence from cache, fallback to DB last_set_presence
     */
    public PresenceStatus getPresence(UUID userId) {
        PresenceStatus cached = presenceCache.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }

        // Fallback to database
        return accountRepository.findById(userId)
                .map(Account::getStLastSetPresence)
                .orElse(PresenceStatus.offline);
    }

    /**
     * Set presence (manual user action)
     */
    public void setPresence(UUID userId, PresenceStatus status) {
        if (status == PresenceStatus.offline) {
            throw new IllegalArgumentException("Cannot manually set offline");
        }

        // Update caches
        presenceCache.put(userId, status);
        if (status != PresenceStatus.idle) {
            lastSetPresenceCache.put(userId, status);
        }

        // Update database for persistence (only last_set_presence matters)
        Account account = accountRepository.findById(userId).orElse(null);
        if (account != null) {
            account.setStPresence(status);
            if (status != PresenceStatus.idle) {
                account.setStLastSetPresence(status);
            }
            accountRepository.save(account);
        }

        // Broadcast with debouncing
        broadcastPresenceUpdate(userId, status);
    }

    /**
     * Set presence automatically (idle/online from tab visibility)
     */
    public void setPresenceAuto(UUID userId, PresenceStatus status) {
        if (status != PresenceStatus.idle && status != PresenceStatus.online) return;

        PresenceStatus lastSet = lastSetPresenceCache.get(userId, id ->
                accountRepository.findById(id)
                        .map(Account::getStLastSetPresence)
                        .orElse(PresenceStatus.online)
        );

        // Only auto-transition if user hasn't explicitly chosen DND or Invisible
        if (lastSet != null && lastSet != PresenceStatus.online) return;

        PresenceStatus current = presenceCache.getIfPresent(userId);
        if (current == status) return; // No change

        presenceCache.put(userId, status);

        // Update DB
        Account account = accountRepository.findById(userId).orElse(null);
        if (account != null) {
            account.setStPresence(status);
            accountRepository.save(account);
        }

        broadcastPresenceUpdate(userId, status);
    }

    /**
     * Mark user as online (called on WebSocket connect)
     */
    public void userConnected(UUID userId) {
        PresenceStatus lastSet = lastSetPresenceCache.get(userId, id ->
                accountRepository.findById(id)
                        .map(Account::getStLastSetPresence)
                        .orElse(PresenceStatus.online)
        );

        // Restore to last set presence (not offline!)
        PresenceStatus target = lastSet != null ? lastSet : PresenceStatus.online;
        if (target == PresenceStatus.invisible) {
            // Invisible users appear offline to others
            presenceCache.put(userId, PresenceStatus.offline);
            broadcastPresenceUpdate(userId, PresenceStatus.offline);
        } else {
            presenceCache.put(userId, target);
            broadcastPresenceUpdate(userId, target);
        }

        log.debug("User {} connected, restored presence: {}", userId, target);
    }

    /**
     * Mark user as disconnected (with delay to handle reloads)
     */
    public void userDisconnected(UUID userId) {
        PresenceStatus current = presenceCache.getIfPresent(userId);
        if (current == null || current == PresenceStatus.offline) return;

        // Check if user has other sessions via WebSocketSessionManager
        // (This will be checked before actually marking offline)
        presenceCache.put(userId, PresenceStatus.offline);

        Account account = accountRepository.findById(userId).orElse(null);
        if (account != null) {
            account.setStPresence(PresenceStatus.offline);
            accountRepository.save(account);
        }

        broadcastPresenceUpdate(userId, PresenceStatus.offline);
        log.debug("User {} marked offline", userId);
    }

    /**
     * Broadcast presence with debouncing
     */
    private void broadcastPresenceUpdate(UUID userId, PresenceStatus status) {
        // Debounce: don't broadcast too frequently
        Long last = lastBroadcastTime.get(userId);
        long now = System.currentTimeMillis();
        if (last != null && (now - last) < MIN_BROADCAST_INTERVAL_MS) {
            log.debug("Debounced presence update for user {}: {}", userId, status);
            return;
        }
        lastBroadcastTime.put(userId, now);

        // Get recipients
        Set<UUID> recipients = getPresenceRecipients(userId);

        Map<String, Object> payload = Map.of(
                "type", "PRESENCE_UPDATE",
                "data", Map.of("userId", userId, "presence", status.name())
        );

        for (UUID recipientId : recipients) {
            chatHandler.sendToUser(recipientId, payload);
        }
    }

    /**
     * Get all users who should receive presence updates for this user
     * (Cached for 60 seconds)
     */
    private final Cache<UUID, Set<UUID>> recipientCache = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(1000)
            .build();

    private Set<UUID> getPresenceRecipients(UUID userId) {
        return recipientCache.get(userId, id -> {
            Set<UUID> recipients = new HashSet<>();
            recipients.addAll(memberRepository.findFriendIds(id));
            recipients.addAll(participantRepository.findOtherParticipantsInAllDMs(id));
            recipients.add(id); // self
            return recipients;
        });
    }

    /**
     * Get all relevant presences for a user (friends + DM participants)
     */
    public List<AccountService.PresenceDto> getRelevantPresences(UUID userId) {
        Set<UUID> recipients = getPresenceRecipients(userId);

        return recipients.stream()
                .map(id -> new AccountService.PresenceDto(id, getPresence(id)))
                .toList();
    }

    /**
     * Invalidate cache for a user (called when friend list changes)
     */
    public void invalidateCache(UUID userId) {
        recipientCache.invalidate(userId);
    }
}