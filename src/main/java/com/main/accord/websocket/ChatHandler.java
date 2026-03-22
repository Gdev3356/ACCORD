package com.main.accord.websocket;

import com.main.accord.domain.message.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatHandler {

    private final SimpMessagingTemplate broker;

    public void broadcastToChannel(UUID channelId, Message message) {
        broker.convertAndSend(
                "/topic/channel." + channelId,
                new ChatEvent("MESSAGE_CREATE", message)
        );
    }

    public void broadcastEditToChannel(UUID channelId, Message message) {
        broker.convertAndSend(
                "/topic/channel." + channelId,
                new ChatEvent("MESSAGE_UPDATE", message)
        );
    }

    public void broadcastDeleteToChannel(UUID channelId, UUID messageId) {
        broker.convertAndSend(
                "/topic/channel." + channelId,
                new ChatEvent("MESSAGE_DELETE", Map.of("idMessage", messageId))
        );
    }

    public void broadcastToDm(UUID conversationId, Object payload) {
        broker.convertAndSend("/topic/dm." + conversationId, payload);
    }

    public void sendToUser(UUID userId, Object payload) {
        broker.convertAndSendToUser(userId.toString(), "/queue/events", payload);
    }

    public record ChatEvent(String type, Object data) {}
}