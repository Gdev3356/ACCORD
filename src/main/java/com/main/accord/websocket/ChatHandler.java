package com.main.accord.websocket;

import com.main.accord.domain.account.Account;
import com.main.accord.domain.account.AccountRepository;
import com.main.accord.domain.account.AccountService;
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
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ChatHandler {

    private final SimpMessagingTemplate broker;
    private final MemberRepository memberRepository;
    private final AccountService accountService;
    private final AccountRepository accountRepository;

    // Track active user sessions
    private final Map<UUID, Integer> userSessionCount = new ConcurrentHashMap<>();

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID userId = getUserIdFromSession(accessor);

        if (userId != null) {
            int count = userSessionCount.merge(userId, 1, Integer::sum);

            if (count == 1) {
                // First connection - user came back online

                // Restore last set presence
                Account account = accountService.getById(userId);
                account.setStPresence(account.getStLastSetPresence());
                accountRepository.save(account);

                // Broadcast restored presence to friends
                broadcastPresenceUpdate(userId, account.getStPresence());
            }
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID userId = getUserIdFromSession(accessor);

        if (userId != null) {
            int count = userSessionCount.merge(userId, -1, Integer::sum);
            if (count <= 0) {
                userSessionCount.remove(userId);
                broadcastOfflineStatus(userId);
            }
        }
    }

    private UUID getUserIdFromSession(StompHeaderAccessor accessor) {
        // Get user ID from session attributes
        Authentication auth = (Authentication) accessor.getUser();
        if (auth != null && auth.getPrincipal() instanceof AccordPrincipal principal) {
            return principal.userId();
        }
        return null;
    }

    private void broadcastPresenceUpdate(UUID userId, PresenceStatus presence) {
        // Send to all friends and servers where user is a member
        List<UUID> friendIds = memberRepository.findFriendIds(userId);
        for (UUID friendId : friendIds) {
            sendToUser(friendId, Map.of(
                    "type", "PRESENCE_UPDATE",
                    "data", Map.of("userId", userId, "presence", presence.name())
            ));
        }
    }

    private void broadcastOfflineStatus(UUID userId) {
        // Send offline status to all friends
        List<UUID> friendIds = memberRepository.findFriendIds(userId);
        for (UUID friendId : friendIds) {
            sendToUser(friendId, Map.of(
                    "type", "PRESENCE_UPDATE",
                    "data", Map.of("userId", userId, "presence", "offline")
            ));
        }
    }

    public void broadcastToChannel(UUID channelId, Object payload) {
        broker.convertAndSend(
                "/topic/channel." + channelId,
                new ChatEvent("MESSAGE_CREATE", payload)
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