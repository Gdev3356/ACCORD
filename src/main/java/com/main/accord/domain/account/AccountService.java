package com.main.accord.domain.account;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.main.accord.common.AccordException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.dm.ParticipantRepository;
import com.main.accord.domain.server.MemberRepository;
import com.main.accord.websocket.ChatHandler;
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

    private final AccountRepository accountRepository;
    private final ChatHandler chatHandler;
    private final MemberRepository memberRepository;
    private final ParticipantRepository participantRepository;

    // Cache for DM participants - expires after 30 seconds
    private Cache<UUID, Set<UUID>> dmParticipantCache;

    @PostConstruct
    public void init() {
        this.dmParticipantCache = Caffeine.newBuilder()
                .expireAfterWrite(30, TimeUnit.SECONDS)
                .maximumSize(1000)
                .build();
    }

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
            if (!handle.equals(account.getDsHandle()) &&
                    accountRepository.existsByDsHandle(handle)) {
                throw new AccordException("That handle is already taken.");
            }
            account.setDsHandle(handle);
        }

        if (req.displayName() != null) account.setDsDisplayName(req.displayName());
        if (req.pronouns()    != null) account.setDsPronouns(req.pronouns());
        if (req.presence()             != null) account.setStPresence(req.presence());
        if (req.notificationsEnabled() != null) account.setStNotificationsEnabled(req.notificationsEnabled());

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

    @Transactional
    public Account updatePresence(UUID userId, PresenceStatus presence) {
        if (presence == PresenceStatus.offline) {
            throw new AccordException("Cannot manually set presence to offline.");
        }
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));
        account.setStPresence(presence);
        account.setStLastSetPresence(presence);
        chatHandler.broadcastPresenceUpdate(userId, presence);
        return accountRepository.save(account);
    }

    @Transactional
    public Account updateNotifications(UUID userId, boolean enabled) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));
        account.setStNotificationsEnabled(enabled);
        return accountRepository.save(account);
    }

    @Transactional
    public void resetToLastPresence(UUID userId) {
        Account account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));
        account.setStPresence(account.getStLastSetPresence());
        accountRepository.save(account);
    }

    public List<PresenceDto> getRelevantPresences(UUID userId) {
        try {
            Set<UUID> ids = new HashSet<>();

            List<UUID> friendIds = memberRepository.findFriendIds(userId);
            ids.addAll(friendIds);

            try {
                Set<UUID> dmParticipants = dmParticipantCache.get(userId, id -> {
                    try {
                        return participantRepository.findRecentDMParticipants(userId);
                    } catch (Exception e) {
                        return new HashSet<>();
                    }
                });

                if (dmParticipants != null) {
                    ids.addAll(dmParticipants);
                }
            } catch (Exception e) {
                // Continue with just friends
            }

            ids.add(userId);

            return accountRepository.findAllById(ids).stream()
                    .map(a -> new PresenceDto(a.getIdUser(), a.getStPresence()))
                    .toList();

        } catch (Exception e) {
            throw new AccordException("Failed to fetch presence data");
        }
    }

    public record PresenceDto(UUID userId, PresenceStatus presence) {}

    public record UpdateProfileRequest(
            String         handle,
            String         displayName,
            String         pronouns,
            PresenceStatus presence,
            Boolean        notificationsEnabled
    ) {}
}