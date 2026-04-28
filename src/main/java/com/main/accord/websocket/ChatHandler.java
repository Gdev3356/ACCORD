package com.main.accord.websocket;

import com.main.accord.domain.account.Account;
import com.main.accord.domain.account.AccountRepository;
import com.main.accord.domain.account.PresenceStatus;
import com.main.accord.domain.dm.ParticipantRepository;
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

    // ── Dependencies ──────────────────────────────────────────────────────────

    private final SimpMessagingTemplate    broker;
    private final MemberRepository         memberRepository;
    private final AccountRepository        accountRepository;
    private final ParticipantRepository    participantRepository;
    private final PresenceStore            presenceStore;
    private final WebSocketSessionManager  sessionManager;

    // ── Session-connect event ─────────────────────────────────────────────────
    //
    // Fired by Spring after the STOMP CONNECTED frame is sent.
    // At this point the session is already registered in WebSocketSessionManager,
    // so we only need to restore the DB-persisted presence if it differs from
    // what's currently live (handles server restarts, stale-session recovery, etc.)

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        UUID userId = getUserIdFromSession(accessor);
        if (userId == null) return;

        Account account = accountRepository.findById(userId).orElse(null);
        if (account == null) return;

        PresenceStatus target = account.getStLastSetPresence() != null
                ? account.getStLastSetPresence()
                : PresenceStatus.online;

        // Don't disturb invisible users — they want to appear offline
        if (target == PresenceStatus.invisible) return;

        PresenceStatus current = presenceStore.get(userId);
        if (current != target) {
            presenceStore.set(userId, target);
            broadcastPresenceUpdate(userId, target);
        }
    }

    // ── Presence event listeners ──────────────────────────────────────────────

    @EventListener
    public void handleUserDisconnected(UserDisconnectedEvent event) {
        UUID userId = event.userId();
        log.info("UserDisconnectedEvent received userId={}", userId);
        broadcastOfflineStatus(userId);
    }

    @EventListener
    public void handleUserForcedOffline(UserForcedOfflineEvent event) {
        broadcastOfflineStatus(event.userId());
    }

    @EventListener
    public void handleUserMarkedIdle(UserMarkedIdleEvent event) {
        broadcastPresenceUpdate(event.userId(), PresenceStatus.idle);
    }

    // ── Presence broadcast ────────────────────────────────────────────────────

    public void broadcastPresenceUpdate(UUID userId, PresenceStatus presence) {

        // Build the full logical recipient list
        List<UUID> friendIds       = memberRepository.findFriendIds(userId);
        List<UUID> dmParticipantIds = participantRepository.findOtherParticipantsInAllDMs(userId);

        Set<UUID> allRecipients = new HashSet<>();
        allRecipients.addAll(friendIds);
        allRecipients.addAll(dmParticipantIds);
        allRecipients.add(userId); // always notify self

        // ── Optimisation: skip users who aren't connected ─────────────────────
        // Sending to an offline user is a no-op on the broker, but it still
        // hits the session lookup and serialises the payload. With a busy server
        // this fan-out can be 80 %+ waste. Filtering to connected users only
        // makes the loop cheap regardless of total friend/DM count.
        Set<UUID> connected = sessionManager.getConnectedUserIds();
        allRecipients.retainAll(connected);

        Map<String, Object> payload = Map.of(
                "type", "PRESENCE_UPDATE",
                "data", Map.of("userId", userId, "presence", presence.name())
        );

        for (UUID recipientId : allRecipients) {
            sendToUser(recipientId, payload);
        }
    }

    private void broadcastOfflineStatus(UUID userId) {
        // Update presence store (lazy DB flush handles the rest)
        presenceStore.set(userId, PresenceStatus.offline);
        broadcastPresenceUpdate(userId, PresenceStatus.offline);
    }

    // ── Channel / DM broadcast ────────────────────────────────────────────────

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

    public void broadcastEventToChannel(UUID channelId, ChatEvent event) {
        broker.convertAndSend("/topic/channel." + channelId, event);
    }

    public void broadcastToServer(UUID serverId, ChatEvent event) {
        List<UUID> memberIds = memberRepository.findUserIdsByServerId(serverId);
        for (UUID userId : memberIds) {
            sendToUser(userId, event);
        }
    }

    public void sendToUser(UUID userId, Object payload) {
        broker.convertAndSendToUser(userId.toString(), "/queue/events", payload);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private UUID getUserIdFromSession(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user == null) return null;

        if (user instanceof Authentication auth) {
            if (auth.getPrincipal() instanceof AccordPrincipal ap) return ap.userId();
        }
        if (user instanceof AccordPrincipal ap) return ap.userId();

        return null;
    }

    public void broadcastToServer(UUID serverId, Object payload) {
        broker.convertAndSend("/topic/server." + serverId, payload);
    }

    // ── Types ─────────────────────────────────────────────────────────────────

    public record ChatEvent(String type, Object data) {}
}