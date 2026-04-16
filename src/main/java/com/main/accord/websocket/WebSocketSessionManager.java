package com.main.accord.websocket;

import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

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
    private final ChatHandler chatHandler;
    private final ConcurrentHashMap<String, UUID> sessionToUser = new ConcurrentHashMap<>();

    record SessionInfo(String sessionId, UUID userId, long connectedAt, long lastHeartbeat) {}

    @EventListener
    public void handleSessionConnected(SessionConnectEvent event) {
        String sessionId = event.getMessage().getHeaders().get("simpSessionId", String.class);

        // Extract user ID from the CONNECT frame
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID userId = extractUserId(accessor);

        if (sessionId != null && userId != null && !activeSessions.containsKey(sessionId)) {
            activeSessions.put(sessionId, new SessionInfo(sessionId, userId, System.currentTimeMillis(), System.currentTimeMillis()));
            sessionToUser.put(sessionId, userId);
            int total = totalConnections.incrementAndGet();
            log.info("WebSocket CONNECTED - Session: {}, User: {}, Active: {}, Total: {}",
                    sessionId, userId, activeSessions.size(), total);
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId != null) {
            SessionInfo removed = activeSessions.remove(sessionId);
            UUID userId = sessionToUser.remove(sessionId);

            if (removed != null) {
                log.info("WebSocket DISCONNECTED - Session: {}, User: {}, Active: {}, Duration: {}ms",
                        sessionId, userId, activeSessions.size(), System.currentTimeMillis() - removed.connectedAt());

                // Notify ChatHandler that this user might be offline
                if (userId != null && !hasOtherSessionsForUser(userId, sessionId)) {
                    // Let ChatHandler handle the actual presence update
                    // ChatHandler will check its own session count
                }
            }
        }
    }

    @Scheduled(fixedDelay = 60000)
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

                log.warn("Removing stale session: {} for user: {}", sessionId, userId);

                // Force disconnect handling for this user
                if (userId != null && !hasOtherSessionsForUser(userId, sessionId)) {
                    // This was the last session - mark user offline
                    chatHandler.forceOffline(userId);
                }

                iterator.remove();
                sessionToUser.remove(sessionId);
                removed++;
            }
        }

        if (removed > 0) {
            log.warn("Cleaned up {} stale sessions, Active now: {}", removed, activeSessions.size());
        }
    }

    private boolean hasOtherSessionsForUser(UUID userId, String excludeSessionId) {
        return activeSessions.entrySet().stream()
                .anyMatch(e -> !e.getKey().equals(excludeSessionId) &&
                        e.getValue().userId().equals(userId));
    }

    private UUID extractUserId(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user instanceof Authentication auth) {
            Object principal = auth.getPrincipal();
            if (principal instanceof AccordPrincipal accordPrincipal) {
                return accordPrincipal.userId();
            }
        }
        return null;
    }
}