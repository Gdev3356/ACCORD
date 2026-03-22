package com.main.accord.websocket;

import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtDecoder jwtDecoder;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");
            if (token != null && token.startsWith("Bearer ")) {
                try {
                    Jwt jwt = jwtDecoder.decode(token.substring(7));
                    AccordPrincipal principal = new AccordPrincipal(
                            UUID.fromString(jwt.getSubject()),
                            jwt.getClaimAsString("email")
                    );
                    accessor.setUser(new StompUser(principal));
                } catch (JwtException e) {
                    throw new IllegalArgumentException("Invalid token on WebSocket connect.");
                }
            }
        }
        return message;
    }
}