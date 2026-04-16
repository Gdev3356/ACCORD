package com.main.accord.domain.account;

import com.main.accord.common.AccordException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.dm.ParticipantRepository;
import com.main.accord.domain.server.MemberRepository;
import com.main.accord.websocket.ChatHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final ChatHandler chatHandler;
    private final MemberRepository memberRepository;
    private final ParticipantRepository participantRepository;

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
        // Reset to last manually set presence (not "invisible" if that's what they had)
        account.setStPresence(account.getStLastSetPresence());
        accountRepository.save(account);
    }

    public List<PresenceDto> getRelevantPresences(UUID userId) {
        try {
            Set<UUID> ids = new HashSet<>();
            ids.addAll(memberRepository.findFriendIds(userId));
            ids.addAll(participantRepository.findOtherParticipantsInAllDMs(userId, OffsetDateTime.now().minusDays(30)));

            return accountRepository.findAllById(ids).stream()
                    .map(a -> new PresenceDto(a.getIdUser(), a.getStPresence()))
                    .toList();
        } catch (Exception e) {
            e.printStackTrace(); // force it to Render logs
            throw e;
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