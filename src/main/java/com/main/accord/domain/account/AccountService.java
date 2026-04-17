package com.main.accord.domain.account;

import com.main.accord.common.AccordException;
import com.main.accord.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final PresenceService presenceService;

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

        // Delegate to PresenceService (handles cache + DB + broadcast)
        presenceService.setPresence(userId, presence);

        // Return updated account
        return accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    // Modify updatePresenceAuto method
    @Transactional
    public void updatePresenceAuto(UUID userId, PresenceStatus presence) {
        if (presence != PresenceStatus.idle && presence != PresenceStatus.online) return;
        presenceService.setPresenceAuto(userId, presence);
    }

    // Modify getRelevantPresences method (MUCH simpler now!)
    public List<PresenceDto> getRelevantPresences(UUID userId) {
        return presenceService.getRelevantPresences(userId);
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