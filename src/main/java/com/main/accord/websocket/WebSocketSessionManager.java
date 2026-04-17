package com.main.accord.websocket;

import com.main.accord.domain.account.PresenceStatus;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class WebSocketSessionManager {

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final ApplicationEventPublisher eventPublisher;
    private final PresenceStore presenceStore;

    // ── Session state ─────────────────────────────────────────────────────────

    private final ConcurrentHashMap<String, SessionInfo> activeSessions  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID>        sessionToUser   = new ConcurrentHashMap<>();
    private final AtomicInteger                          totalConnections = new AtomicInteger(0);

    // ── Debounce: pending offline events ─────────────────────────────────────
    //
    // When a disconnect arrives we don't fire UserDisconnectedEvent immediately.
    // Instead we schedule it 6 seconds out. If the same user reconnects within
    // that window (page refresh, brief network blip) we cancel the scheduled
    // event — no spurious offline broadcast, no wasted DB write.

    private final ScheduledExecutorService debounceScheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "presence-debounce");
                t.setDaemon(true);
                return t;
            });

    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> pendingDisconnects =
            new ConcurrentHashMap<>();

    // ── Session info ──────────────────────────────────────────────────────────

    record SessionInfo(String sessionId, UUID userId, long connectedAt, long lastHeartbeat) {}

    // ── Connect ───────────────────────────────────────────────────────────────

    @EventListener
    public void handleSessionConnected(SessionConnectEvent event) {
        String sessionId = event.getMessage().getHeaders().get("simpSessionId", String.class);
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID userId = extractUserId(accessor);

        if (sessionId == null || userId == null || activeSessions.containsKey(sessionId)) return;

        activeSessions.put(sessionId,
                new SessionInfo(sessionId, userId, System.currentTimeMillis(), System.currentTimeMillis()));
        sessionToUser.put(sessionId, userId);
        int total = totalConnections.incrementAndGet();

        log.info("WS CONNECTED  session={} user={} active={} total={}",
                sessionId, userId, activeSessions.size(), total);

        // Cancel any pending offline event — the user is back
        cancelPendingDisconnect(userId);
    }

    // ── Disconnect ────────────────────────────────────────────────────────────

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        SessionInfo removed = activeSessions.remove(sessionId);
        UUID userId        = sessionToUser.remove(sessionId);

        log.info("WS DISCONNECT session={} user={}", sessionId, userId);

        if (removed == null || userId == null) return;

        log.info("WS DISCONNECTED session={} user={} active={} duration={}ms",
                sessionId, userId, activeSessions.size(),
                System.currentTimeMillis() - removed.connectedAt());

        if (!hasOtherSessionsForUser(userId, sessionId)) {
            schedulePendingDisconnect(userId);
        }
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    /** Called by the STOMP interceptor on every inbound frame. */
    public void updateHeartbeat(String sessionId) {
        if (sessionId != null) {
            activeSessions.computeIfPresent(sessionId, (id, old) ->
                    new SessionInfo(id, old.userId(), old.connectedAt(), System.currentTimeMillis()));
        }
    }

    // ── Stale session cleanup ─────────────────────────────────────────────────

    @Scheduled(fixedDelay = 60_000)
    public void cleanupStaleSessions() {
        long now            = System.currentTimeMillis();
        long staleThreshold = now - (3 * 60 * 1_000); // 3 minutes without a heartbeat

        int removed = 0;
        var iterator = activeSessions.entrySet().iterator();

        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().lastHeartbeat() < staleThreshold) {
                String sessionId = entry.getKey();
                UUID   userId    = sessionToUser.get(sessionId);

                log.warn("Stale session removed: session={} user={} age={}ms",
                        sessionId, userId, now - entry.getValue().lastHeartbeat());

                if (userId != null && !hasOtherSessionsForUser(userId, sessionId)) {
                    eventPublisher.publishEvent(new UserForcedOfflineEvent(userId));
                }

                iterator.remove();
                sessionToUser.remove(sessionId);
                removed++;
            }
        }

        checkIdleUsers(now);

        if (removed > 0) {
            log.info("Stale cleanup: removed={} remaining={}", removed, activeSessions.size());
        }
    }

    // ── Public query ──────────────────────────────────────────────────────────

    /**
     * Returns a snapshot of currently-connected user IDs.
     * Used by ChatHandler to skip pushes to offline users.
     */
    public Set<UUID> getConnectedUserIds() {
        return Set.copyOf(sessionToUser.values());
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void schedulePendingDisconnect(UUID userId) {
        // If there's already one pending, don't replace it
        if (pendingDisconnects.containsKey(userId)) return;

        ScheduledFuture<?> future = debounceScheduler.schedule(() -> {
            pendingDisconnects.remove(userId);
            // Fire only if the user is still genuinely offline
            if (!hasOtherSessionsForUser(userId, null)) {
                log.info("Debounce expired — firing UserDisconnectedEvent for user={}", userId);
                eventPublisher.publishEvent(new UserDisconnectedEvent(userId));
            }
        }, 6, TimeUnit.SECONDS);

        pendingDisconnects.put(userId, future);
        log.debug("Pending disconnect scheduled for user={}", userId);
    }

    private void cancelPendingDisconnect(UUID userId) {
        ScheduledFuture<?> pending = pendingDisconnects.remove(userId);
        if (pending != null && pending.cancel(false)) {
            log.debug("Pending disconnect cancelled for user={} (reconnected)", userId);
        }
    }

    private void checkIdleUsers(long now) {
        long idleThreshold = now - (5 * 60 * 1_000); // 5 minutes

        activeSessions.values().stream()
                .map(SessionInfo::userId)
                .distinct()
                .forEach(userId -> {
                    boolean hasRecentActivity = activeSessions.values().stream()
                            .anyMatch(s -> s.userId().equals(userId)
                                    && s.lastHeartbeat() > idleThreshold);

                    if (!hasRecentActivity && presenceStore.is(userId, PresenceStatus.online)) {
                        presenceStore.set(userId, PresenceStatus.idle);
                        eventPublisher.publishEvent(new UserMarkedIdleEvent(userId));
                        log.info("User {} marked idle due to inactivity", userId);
                    }
                });
    }

    private boolean hasOtherSessionsForUser(UUID userId, String excludeSessionId) {
        return activeSessions.entrySet().stream()
                .anyMatch(e -> !e.getKey().equals(excludeSessionId)
                        && e.getValue().userId().equals(userId));
    }

    private UUID extractUserId(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) return null;

        if (user instanceof StompUser stompUser) {
            Principal inner = stompUser.principal();
            if (inner instanceof AccordPrincipal ap) return ap.userId();
        }
        if (user instanceof Authentication auth) {
            if (auth.getPrincipal() instanceof AccordPrincipal ap) return ap.userId();
        }
        if (user instanceof AccordPrincipal ap) return ap.userId();

        return null;
    }
}