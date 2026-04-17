package com.main.accord.websocket;

import com.main.accord.domain.account.PresenceStatus;
import com.main.accord.domain.message.Message;
import com.main.accord.domain.server.MemberRepository;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;

import java.security.Principal;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHandler {

    private final SimpMessagingTemplate broker;
    private final MemberRepository memberRepository;
    private final PresenceBroadcastService presenceService;

    @EventListener
    public void handleUserForcedOffline(UserForcedOfflineEvent event) {
        presenceService.userDisconnected(event.userId());
    }

    @EventListener
    public void handleUserMarkedIdle(UserMarkedIdleEvent event) {
        presenceService.setPresenceAuto(event.userId(), PresenceStatus.idle);
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID userId = getUserIdFromSession(accessor);
        if (userId == null) return;

        log.debug("Session connected for user: {}", userId);
        presenceService.userConnected(userId);
    }

    @EventListener
    public void handleUserDisconnected(UserDisconnectedEvent event) {
        log.info("User disconnected event: {}", event.userId());
        presenceService.userDisconnected(event.userId());
    }

    private UUID getUserIdFromSession(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) return null;

        if (user instanceof Authentication auth) {
            Object principal = auth.getPrincipal();
            if (principal instanceof AccordPrincipal accordPrincipal) {
                return accordPrincipal.userId();
            }
        }

        if (user instanceof AccordPrincipal accordPrincipal) {
            return accordPrincipal.userId();
        }

        return null;
    }

    // Keep existing broadcast methods (they delegate to PresenceService now)
    public void broadcastPresenceUpdate(UUID userId, PresenceStatus presence) {
        // This is now handled by PresenceService, but keep for backward compatibility
        presenceService.getRelevantPresences(userId); // Just to trigger cache
    }

    public void sendToUser(UUID userId, Object payload) {
        broker.convertAndSendToUser(userId.toString(), "/queue/events", payload);
    }

    // Keep all your existing channel/DM broadcast methods unchanged
    public void broadcastToChannel(UUID channelId, Object payload) {
        broker.convertAndSend("/topic/channel." + channelId, new ChatEvent("MESSAGE_CREATE", payload));
    }

    public void broadcastEditToChannel(UUID channelId, Message message) {
        broker.convertAndSend("/topic/channel." + channelId, new ChatEvent("MESSAGE_UPDATE", message));
    }

    public void broadcastDeleteToChannel(UUID channelId, UUID messageId) {
        broker.convertAndSend("/topic/channel." + channelId, new ChatEvent("MESSAGE_DELETE", Map.of("idMessage", messageId)));
    }

    public void broadcastToDm(UUID conversationId, Object payload) {
        broker.convertAndSend("/topic/dm." + conversationId, payload);
    }

    public void broadcastEventToChannel(UUID channelId, ChatEvent event) {
        broker.convertAndSend("/topic/channel." + channelId, event);
    }

    public void broadcastToServer(UUID serverId, ChatEvent event) {
        List<UUID> memberIds = memberRepository.findUserIdsByServerId(serverId);
        for (UUID userId : memberIds) {
            sendToUser(userId, event);
        }
    }

    public record ChatEvent(String type, Object data) {}
}