package com.main.accord.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@EnableScheduling
public class WebSocketSessionManager {

    private final ConcurrentHashMap<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger totalConnections = new AtomicInteger(0);

    record SessionInfo(String sessionId, long connectedAt, long lastHeartbeat) {}

    @EventListener
    public void handleSessionConnected(SessionConnectEvent event) {
        String sessionId = event.getMessage().getHeaders().get("simpSessionId", String.class);
        if (sessionId != null && !activeSessions.containsKey(sessionId)) {
            activeSessions.put(sessionId, new SessionInfo(sessionId, System.currentTimeMillis(), System.currentTimeMillis()));
            int total = totalConnections.incrementAndGet();
            log.info("WebSocket CONNECTED - Session: {}, Active: {}, Total: {}",
                    sessionId, activeSessions.size(), total);
        } else if (sessionId != null) {
            log.warn("Duplicate CONNECT for session: {}", sessionId);
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId != null) {
            SessionInfo removed = activeSessions.remove(sessionId);
            if (removed != null) {
                log.info("WebSocket DISCONNECTED - Session: {}, Active: {}, Duration: {}ms",
                        sessionId, activeSessions.size(), System.currentTimeMillis() - removed.connectedAt());
            } else {
                log.warn("DISCONNECT for unknown session: {}", sessionId);
            }
        }
    }

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        String sessionId = event.getMessage().getHeaders().get("simpSessionId", String.class);
        String destination = (String) event.getMessage().getHeaders().get("simpDestination");

        // Update heartbeat
        if (sessionId != null && activeSessions.containsKey(sessionId)) {
            activeSessions.put(sessionId, new SessionInfo(
                    sessionId,
                    activeSessions.get(sessionId).connectedAt(),
                    System.currentTimeMillis()
            ));
        }

        log.debug("SUBSCRIBE - Session: {}, Destination: {}", sessionId, destination);
    }

    @EventListener
    public void handleSessionUnsubscribe(SessionUnsubscribeEvent event) {
        String sessionId = event.getMessage().getHeaders().get("simpSessionId", String.class);

        if (sessionId != null && activeSessions.containsKey(sessionId)) {
            activeSessions.put(sessionId, new SessionInfo(
                    sessionId,
                    activeSessions.get(sessionId).connectedAt(),
                    System.currentTimeMillis()  // ← refresh heartbeat
            ));
        }

        log.debug("UNSUBSCRIBE - Session: {}", sessionId);
    }

    // Clean up stale sessions every minute (more aggressive)
    @Scheduled(fixedDelay = 60000) // 1 minute
    public void cleanupStaleSessions() {
        long now = System.currentTimeMillis();
        long staleThreshold = now - (10 * 60 * 1000); // 3 minutes without activity

        int removed = 0;
        var iterator = activeSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().lastHeartbeat() < staleThreshold) {
                log.warn("Removing stale session: {} (last activity: {}ms ago)",
                        entry.getKey(), now - entry.getValue().lastHeartbeat());
                iterator.remove();
                removed++;
            }
        }

        if (removed > 0) {
            log.warn("Cleaned up {} stale sessions, Active now: {}", removed, activeSessions.size());
        }

        log.debug("Session stats - Active: {}, Total connections: {}", activeSessions.size(), totalConnections.get());
    }

    public int getCurrentConnections() {
        return activeSessions.size();
    }

    public int getTotalConnections() {
        return totalConnections.get();
    }
}