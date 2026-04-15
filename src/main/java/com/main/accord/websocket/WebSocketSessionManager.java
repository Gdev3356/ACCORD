package com.main.accord.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.user.SimpUserRegistry;
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
@RequiredArgsConstructor
public class WebSocketSessionManager {

    private final SimpUserRegistry userRegistry;
    private final SimpMessageSendingOperations messagingTemplate;

    // Track active sessions with timestamps
    private final ConcurrentHashMap<String, Long> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger currentConnections = new AtomicInteger(0);

    @EventListener
    public void handleSessionConnected(SessionConnectEvent event) {
        String sessionId = event.getMessage().getHeaders().get("simpSessionId", String.class);
        if (sessionId != null) {
            activeSessions.put(sessionId, System.currentTimeMillis());
            int current = currentConnections.incrementAndGet();
            int total = totalConnections.incrementAndGet();
            log.info("WebSocket connected - Session: {}, Current active: {}, Total: {}",
                    sessionId, current, total);
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId != null) {
            activeSessions.remove(sessionId);
            int current = currentConnections.decrementAndGet();
            log.info("WebSocket disconnected - Session: {}, Current active: {}",
                    sessionId, current);
        }
    }

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        String sessionId = event.getMessage().getHeaders().get("simpSessionId", String.class);
        String destination = (String) event.getMessage().getHeaders().get("simpDestination");
        log.debug("Subscription - Session: {}, Destination: {}", sessionId, destination);
    }

    @EventListener
    public void handleSessionUnsubscribe(SessionUnsubscribeEvent event) {
        String sessionId = event.getMessage().getHeaders().get("simpSessionId", String.class);
        log.debug("Unsubscription - Session: {}", sessionId);
    }

    // Clean up stale sessions every 5 minutes
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void cleanupStaleSessions() {
        long now = System.currentTimeMillis();
        long staleThreshold = now - (2 * 60 * 1000); // 2 minutes without heartbeat

        activeSessions.entrySet().removeIf(entry -> {
            if (entry.getValue() < staleThreshold) {
                log.warn("Removing stale session: {}", entry.getKey());
                return true;
            }
            return false;
        });

        // Log current stats
        log.debug("Session stats - Active: {}, Total connections: {}",
                currentConnections.get(), totalConnections.get());
    }

    // Update heartbeat timestamp (call this when receiving messages)
    public void updateHeartbeat(String sessionId) {
        if (sessionId != null) {
            activeSessions.put(sessionId, System.currentTimeMillis());
        }
    }

    public int getCurrentConnections() {
        return currentConnections.get();
    }

    public int getTotalConnections() {
        return totalConnections.get();
    }
}