package com.main.accord.domain.account;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.main.accord.common.AccordException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.dm.ParticipantRepository;
import com.main.accord.domain.server.MemberRepository;
import com.main.accord.websocket.ChatHandler;
import com.main.accord.websocket.PresenceStore;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AccountService {

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final AccountRepository   accountRepository;
    private final ChatHandler         chatHandler;
    private final MemberRepository    memberRepository;
    private final ParticipantRepository participantRepository;
    private final PresenceStore       presenceStore;

    // Cache for DM participants — expires after 30 seconds to avoid stale data
    private Cache<UUID, Set<UUID>> dmParticipantCache;

    @PostConstruct
    public void init() {
        this.dmParticipantCache = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .maximumSize(1_000)
                .build();
    }

    // ── Profile ───────────────────────────────────────────────────────────────

    public Account getByHandle(String handle) {
        return accountRepository.findByDsHandleIgnoreCase(handle)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    public Account getById(UUID userId) {
        return accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    @Transactional
    public Account updateProfile(UUID userId, UpdateProfileRequest req) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));

        if (req.handle() != null) {
            String handle = req.handle().toLowerCase().trim();
            if (!handle.equals(account.getDsHandle())
                    && accountRepository.existsByDsHandle(handle)) {
                throw new AccordException("That handle is already taken.");
            }
            account.setDsHandle(handle);
        }

        if (req.displayName() != null) account.setDsDisplayName(req.displayName());
        if (req.pronouns()    != null) account.setDsPronouns(req.pronouns());
        if (req.notificationsEnabled() != null)
            account.setStNotificationsEnabled(req.notificationsEnabled());

        // Presence changes via updateProfile are treated as explicit user intent
        if (req.presence() != null) {
            updatePresence(userId, req.presence());
        }

        return accountRepository.save(account);
    }

    @Transactional
    public void updateLastLogin(UUID userId) {
        accountRepository.findById(userId).ifPresent(a -> {
            a.setDtLastLogin(OffsetDateTime.now());
            accountRepository.save(a);
        });
    }

    @Transactional
    public void softDelete(UUID userId) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));
        account.setStActive(false);
        accountRepository.save(account);
    }

    // ── Presence ──────────────────────────────────────────────────────────────

    /**
     * Explicit user-triggered presence change (e.g. picking "Do Not Disturb" from the UI).
     *
     * Uses setAndFlush() so the chosen status is persisted immediately — it needs
     * to survive a server restart before the next scheduled flush window closes.
     * All other internal presence transitions use presenceStore.set() (lazy).
     */
    @Transactional
    public Account updatePresence(UUID userId, PresenceStatus presence) {
        if (presence == PresenceStatus.offline) {
            throw new AccordException("Cannot manually set presence to offline.");
        }

        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));

        // Persist stLastSetPresence so reconnects restore the right status
        if (presence != PresenceStatus.idle) {
            account.setStLastSetPresence(presence);
            accountRepository.save(account);
        }

        // Write to in-memory store AND immediately flush stPresence to DB
        presenceStore.setAndFlush(userId, presence);
        chatHandler.broadcastPresenceUpdate(userId, presence);

        return account;
    }

    @Transactional
    public Account updateNotifications(UUID userId, boolean enabled) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));
        account.setStNotificationsEnabled(enabled);
        return accountRepository.save(account);
    }

    /**
     * Internal auto-transition (connect / idle timer).
     * Does NOT touch stLastSetPresence and does NOT flush to DB immediately —
     * the scheduled flush handles persistence.
     */
    @Transactional
    public void updatePresenceAuto(UUID userId, PresenceStatus presence) {
        if (presence != PresenceStatus.idle && presence != PresenceStatus.online) return;

        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));

        PresenceStatus lastSet = account.getStLastSetPresence();

        // Only auto-transition if the user hasn't explicitly chosen something else
        if (lastSet != null && lastSet != PresenceStatus.online) return;

        presenceStore.set(userId, presence);
        chatHandler.broadcastPresenceUpdate(userId, presence);
        // stLastSetPresence intentionally NOT touched
    }

    // ── Presence query ────────────────────────────────────────────────────────

    /**
     * Returns presence for all users relevant to userId:
     * their friends + DM participants + themselves.
     *
     * Live status is served from PresenceStore (in-memory) — no DB read per user.
     */
    public List<PresenceDto> getRelevantPresences(UUID userId) {
        try {
            Set<UUID> ids = new HashSet<>();

            ids.addAll(memberRepository.findFriendIds(userId));

            try {
                Set<UUID> dmParticipants = dmParticipantCache.get(userId, id -> {
                    try {
                        return participantRepository.findRecentDMParticipants(userId);
                    } catch (Exception e) {
                        return new HashSet<>();
                    }
                });
                if (dmParticipants != null) ids.addAll(dmParticipants);
            } catch (Exception ignored) {
                // Degrade gracefully — friends only
            }

            ids.add(userId);

            // Serve presence from in-memory store, no DB reads needed
            return ids.stream()
                    .map(id -> new PresenceDto(id, presenceStore.get(id)))
                    .toList();

        } catch (Exception e) {
            throw new AccordException("Failed to fetch presence data");
        }
    }

    // ── Types ─────────────────────────────────────────────────────────────────

    public record PresenceDto(UUID userId, PresenceStatus presence) {}

    public record UpdateProfileRequest(
            String         handle,
            String         displayName,
            String         pronouns,
            PresenceStatus presence,
            Boolean        notificationsEnabled
    ) {}
}