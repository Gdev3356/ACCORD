package com.main.accord.domain.notification;

import com.main.accord.domain.message.Message;
import com.main.accord.domain.message.MentionParser;
import com.main.accord.websocket.ChatHandler;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ChatHandler            chatHandler;

    @Async  // fire-and-forget so it doesn't block the HTTP response
    public void dispatchMentionNotifications(
            Message message,
            MentionParser.MentionResult mentions,
            UUID serverId,
            UUID authorId) {

        for (UUID userId : mentions.mentionedUserIds()) {
            // Don't notify yourself
            if (userId.equals(authorId)) continue;

            String title = mentions.everyonePinged() ? "@everyone" : "You were mentioned";

            Notification notif = notificationRepository.save(
                    Notification.builder()
                            .idUser(userId)
                            .tpNotif(NotifType.mention)
                            .dsTitle(title)
                            .dsBody("New mention in a channel")
                            .jsPayload(Map.of(
                                    "messageId",  message.getIdMessage().toString(),
                                    "channelId",  message.getIdChannel().toString(),
                                    "serverId",   serverId.toString(),
                                    "authorId",   authorId.toString()
                            ))
                            .build()
            );

            // Push to user's notification queue in real time
            chatHandler.sendToUser(userId, Map.of(
                    "type", "MENTION",
                    "data", notif
            ));
        }
    }

    public void markRead(UUID notifId, UUID userId) {
        notificationRepository.findById(notifId).ifPresent(n -> {
            if (!n.getIdUser().equals(userId)) return;
            n.setStRead(true);
            n.setDtRead(java.time.OffsetDateTime.now());
            notificationRepository.save(n);
        });
    }

    @Transactional   // ← was missing
    public void markAllRead(UUID userId) {
        List<Notification> unread = notificationRepository.findUnreadByUser(userId);
        unread.forEach(n -> {
            n.setStRead(true);
            n.setDtRead(OffsetDateTime.now());
        });
        notificationRepository.saveAll(unread);  // ← was missing
    }
}
