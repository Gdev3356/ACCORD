package com.main.accord.websocket;

import com.main.accord.domain.account.AccountRepository;
import com.main.accord.domain.account.PresenceService;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class WebSocketSessionManager {

    private final ConcurrentHashMap<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final ApplicationEventPublisher eventPublisher;
    private final AccountRepository accountRepository;
    private final ConcurrentHashMap<String, UUID> sessionToUser = new ConcurrentHashMap<>();
    private final PresenceService presenceService;

    record SessionInfo(String sessionId, UUID userId, long connectedAt, long lastHeartbeat) {}

    @EventListener
    public void handleSessionConnected(SessionConnectEvent event) {
        String sessionId = event.getMessage().getHeaders().get("simpSessionId", String.class);
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID userId = extractUserId(accessor);

        if (sessionId != null && userId != null && !activeSessions.containsKey(sessionId)) {
            activeSessions.put(sessionId, new SessionInfo(sessionId, userId, System.currentTimeMillis(), System.currentTimeMillis()));
            sessionToUser.put(sessionId, userId);
            int total = totalConnections.incrementAndGet();
            log.info("WebSocket CONNECTED - Session: {}, User: {}, Active: {}, Total: {}",
                    sessionId, userId, activeSessions.size(), total);

            // Let PresenceService know about connection (for presence restoration)
            presenceService.userConnected(userId);
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        SessionInfo removed = activeSessions.remove(sessionId);
        UUID userId = sessionToUser.remove(sessionId);  // ← always reliable here
        log.info("DISCONNECT EVENT - sessionId: {}, resolved userId: {}", sessionId, userId);

        if (removed != null && userId != null) {
            log.info("WebSocket DISCONNECTED - Session: {}, User: {}, Active: {}, Duration: {}ms",
                    sessionId, userId, activeSessions.size(), System.currentTimeMillis() - removed.connectedAt());

            if (!hasOtherSessionsForUser(userId, sessionId)) {
                eventPublisher.publishEvent(new UserDisconnectedEvent(userId));
            }
        }
    }

    // Public method called by the interceptor
    public void updateHeartbeat(String sessionId) {
        if (sessionId != null) {
            activeSessions.computeIfPresent(sessionId, (id, old) ->
                    new SessionInfo(id, old.userId(), old.connectedAt(), System.currentTimeMillis())
            );
        }
    }

    @Scheduled(fixedDelay = 60000) // Every minute
    public void cleanupStaleSessions() {
        long now = System.currentTimeMillis();
        long staleThreshold = now - (3 * 60 * 1000); // 3 minutes

        int removed = 0;
        var iterator = activeSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().lastHeartbeat() < staleThreshold) {
                String sessionId = entry.getKey();
                UUID userId = sessionToUser.get(sessionId);

                log.warn("Removing stale session: {} for user: {} (last heartbeat: {}ms ago)",
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
            log.info("Cleaned up {} stale sessions, {} active sessions remaining",
                    removed, activeSessions.size());
        }
    }

    private void checkIdleUsers(long now) {
        long idleThreshold = now - (5 * 60 * 1000);

        activeSessions.values().stream()
                .map(SessionInfo::userId)
                .distinct()
                .forEach(userId -> {
                    boolean hasRecentActivity = activeSessions.values().stream()
                            .anyMatch(s -> s.userId().equals(userId) && s.lastHeartbeat() > idleThreshold);

                    if (!hasRecentActivity) {
                        // Use PresenceService instead of direct DB
                        presenceService.setPresenceAuto(userId, PresenceStatus.idle);
                        log.info("User {} marked as idle due to inactivity", userId);
                    }
                });
    }

    private boolean hasOtherSessionsForUser(UUID userId, String excludeSessionId) {
        return activeSessions.entrySet().stream()
                .anyMatch(e -> !e.getKey().equals(excludeSessionId) &&
                        e.getValue().userId().equals(userId));
    }

    private UUID extractUserId(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) return null;

        // Your interceptor wraps it in StompUser
        if (user instanceof StompUser stompUser) {
            Principal inner = stompUser.principal();
            if (inner instanceof AccordPrincipal accordPrincipal) {
                return accordPrincipal.userId();
            }
        }

        // Fallback: Authentication wrapper (SessionConnectedEvent path)
        if (user instanceof Authentication auth) {
            Object principal = auth.getPrincipal();
            if (principal instanceof AccordPrincipal accordPrincipal) {
                return accordPrincipal.userId();
            }
        }

        // Direct AccordPrincipal
        if (user instanceof AccordPrincipal accordPrincipal) {
            return accordPrincipal.userId();
        }

        return null;
    }
}