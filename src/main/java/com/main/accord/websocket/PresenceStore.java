package com.main.accord.websocket;

import com.main.accord.domain.account.Account;
import com.main.accord.domain.account.AccountRepository;
import com.main.accord.domain.account.PresenceStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds live presence state in memory.
 * Reads are served instantly from the map.
 * DB writes are batched every 2 minutes instead of on every change.
 *
 * Use set() for all transient changes (connect / disconnect / idle).
 * Use setAndFlush() only when the user explicitly changes their own presence —
 * that's the one case where the DB value needs to be fresh immediately
 * (e.g. so it survives a server restart before the next scheduled flush).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceStore {

    private final AccountRepository accountRepository;

    private final ConcurrentHashMap<UUID, PresenceStatus> live = new ConcurrentHashMap<>();

    // ── Read ──────────────────────────────────────────────────────────────────

    public PresenceStatus get(UUID userId) {
        return live.getOrDefault(userId, PresenceStatus.offline);
    }

    public boolean is(UUID userId, PresenceStatus status) {
        return get(userId) == status;
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /** In-memory only. DB is updated on the next scheduled flush. */
    public void set(UUID userId, PresenceStatus status) {
        live.put(userId, status);
    }

    /**
     * Write to memory AND immediately persist to DB.
     * Use this only for explicit user-triggered presence changes so that the
     * chosen status survives a server restart before the flush window closes.
     */
    public void setAndFlush(UUID userId, PresenceStatus status) {
        live.put(userId, status);
        persistOne(userId, status);
    }

    /** Remove a user's entry (e.g. after a confirmed disconnect). */
    public void evict(UUID userId) {
        live.remove(userId);
    }

    // ── Scheduled flush ───────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 120_000)
    public void flushToDB() {
        if (live.isEmpty()) return;

        int updated = 0;
        for (var entry : live.entrySet()) {
            boolean changed = persistOne(entry.getKey(), entry.getValue());
            if (changed) updated++;
        }

        if (updated > 0) {
            log.debug("PresenceStore flush: {} account(s) updated in DB", updated);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** @return true if the DB row was actually written */
    private boolean persistOne(UUID userId, PresenceStatus status) {
        Account account = accountRepository.findById(userId).orElse(null);
        if (account == null) return false;
        if (account.getStPresence() == status) return false;

        account.setStPresence(status);
        accountRepository.save(account);
        return true;
    }
}