package com.main.accord.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketHeartbeatInterceptor implements ChannelInterceptor {

    private final WebSocketSessionManager sessionManager;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        String sessionId = accessor.getSessionId();

        // Update heartbeat on any message
        if (sessionId != null) {
            sessionManager.updateHeartbeat(sessionId);
        }

        // Log connection/disconnection for debugging
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.debug("STOMP CONNECT from session: {}", sessionId);
        } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            log.debug("STOMP DISCONNECT from session: {}", sessionId);
        }

        return message;
    }
}