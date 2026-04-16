package com.main.accord.websocket;

import com.main.accord.domain.account.Account;
import com.main.accord.domain.account.AccountRepository;
import com.main.accord.domain.account.PresenceStatus;
import com.main.accord.domain.dm.ParticipantRepository;
import com.main.accord.domain.message.Message;
import com.main.accord.domain.server.MemberRepository;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ChatHandler {

    private final SimpMessagingTemplate broker;
    private final MemberRepository memberRepository;
    private final AccountRepository accountRepository;
    private final ParticipantRepository participantRepository;

    // Track active user sessions
    private final Map<UUID, Integer> userSessionCount = new ConcurrentHashMap<>();

    @EventListener
    public void handleUserForcedOffline(UserForcedOfflineEvent event) {
        forceOffline(event.userId());
    }

    @EventListener
    public void handleUserMarkedIdle(UserMarkedIdleEvent event) {
        broadcastPresenceUpdate(event.userId(), PresenceStatus.idle);
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID userId = getUserIdFromSession(accessor);

        if (userId != null) {
            int count = userSessionCount.merge(userId, 1, Integer::sum);

            if (count == 1) {
                // First connection - user came back online

                // Restore last set presence
                Account account = accountRepository.findById(userId)
                        .orElseThrow(() -> new RuntimeException("User not found: " + userId));

                PresenceStatus restored = account.getStLastSetPresence() != null
                        ? account.getStLastSetPresence()
                        : PresenceStatus.online;

                account.setStPresence(restored);
                accountRepository.save(account);
                broadcastPresenceUpdate(userId, restored);
            }
        }
    }

    /**
     * Force a user offline - called when stale sessions are cleaned up
     */
    public void forceOffline(UUID userId) {
        // Check if user still has any active sessions
        if (!userSessionCount.containsKey(userId) || userSessionCount.get(userId) <= 0) {
            // Update database
            Account account = accountRepository.findById(userId).orElse(null);
            if (account != null && account.getStPresence() != PresenceStatus.invisible) {
                // Don't change invisible users - they want to appear offline
                broadcastOfflineStatus(userId);
            }

            // Clear session tracking
            userSessionCount.remove(userId);
        }
    }

    @EventListener
    public void handleUserDisconnected(UserDisconnectedEvent event) {
        UUID userId = event.userId();
        userSessionCount.remove(userId);

        Account account = accountRepository.findById(userId).orElse(null);
        if (account != null && account.getStPresence() != PresenceStatus.invisible) {
            broadcastOfflineStatus(userId);
        }
    }


    private UUID getUserIdFromSession(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) return null;

        // Try Authentication wrapper first (most common)
        if (user instanceof Authentication auth) {
            Object principal = auth.getPrincipal();
            if (principal instanceof AccordPrincipal accordPrincipal) {
                return accordPrincipal.userId();
            }
        }

        // Fallback: direct AccordPrincipal (unlikely but possible)
        if (user instanceof AccordPrincipal accordPrincipal) {
            return accordPrincipal.userId();
        }

        return null;
    }

    public void broadcastPresenceUpdate(UUID userId, PresenceStatus presence) {

        // 1. Send to all friends
        List<UUID> friendIds = memberRepository.findFriendIds(userId);

        // 2. Send to all DM conversation participants
        List<UUID> dmParticipantIds = participantRepository.findOtherParticipantsInAllDMs(userId);

        // Combine unique user IDs
        Set<UUID> allRecipients = new HashSet<>();
        allRecipients.addAll(friendIds);
        allRecipients.addAll(dmParticipantIds);
        allRecipients.add(userId); // Include self

        Map<String, Object> payload = Map.of(
                "type", "PRESENCE_UPDATE",
                "data", Map.of("userId", userId, "presence", presence.name())
        );

        for (UUID recipientId : allRecipients) {
            sendToUser(recipientId, payload);
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
        Account account = accountRepository.findById(userId).orElse(null);
        if (account != null) {
            account.setStPresence(PresenceStatus.offline);
            accountRepository.save(account);
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