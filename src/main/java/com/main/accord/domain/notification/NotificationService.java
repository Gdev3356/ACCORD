package com.main.accord.domain.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.main.accord.domain.message.Message;
import com.main.accord.domain.message.MentionParser;
import com.main.accord.security.EncryptionService;
import com.main.accord.websocket.ChatHandler;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final ChatHandler            chatHandler;
    private final EncryptionService      encryptionService;
    private final ObjectMapper           objectMapper;

    // -------------------------------------------------------------------------
    //  Core dispatch
    // -------------------------------------------------------------------------

    @Async
    public void dispatchMentionNotifications(
            Message message,
            MentionParser.MentionResult mentions,
            UUID serverId,
            UUID authorId) {

        for (UUID userId : mentions.mentionedUserIds()) {
            if (userId.equals(authorId)) continue;

            String title = mentions.everyonePinged() ? "@everyone" : "You were mentioned";
            String body  = "New mention in a channel";

            Map<String, Object> payload = Map.of(
                    "messageId", message.getIdMessage().toString(),
                    "channelId", message.getIdChannel().toString(),
                    "serverId",  serverId.toString(),
                    "authorId",  authorId.toString()
            );

            Notification notif = buildAndSave(userId, NotifType.mention, title, body, payload);

            // Broadcast the decrypted copy — clients must never receive ciphertext
            chatHandler.sendToUser(userId, Map.of(
                    "type", "MENTION",
                    "data", toWireDto(notif, title, body, payload)
            ));
        }
    }

    @Async
    public void send(UUID recipientId, NotifType type,
                     String title, String body,
                     Map<String, Object> payload) {

        Notification notif = buildAndSave(recipientId, type, title, body, payload);

        chatHandler.sendToUser(recipientId, Map.of(
                "type", "NOTIFICATION",
                "data", toWireDto(notif, title, body, payload)
        ));
    }

    // -------------------------------------------------------------------------
    //  Read-state mutations  (no decryption needed — just flipping flags)
    // -------------------------------------------------------------------------

    public void markRead(UUID notifId, UUID userId) {
        notificationRepository.findById(notifId).ifPresent(n -> {
            if (!n.getIdUser().equals(userId)) return;
            n.setStRead(true);
            n.setDtRead(OffsetDateTime.now());
            notificationRepository.save(n);
        });
    }

    @Transactional
    public void markAllRead(UUID userId) {
        List<Notification> unread = notificationRepository.findUnreadByUser(userId);
        unread.forEach(n -> {
            n.setStRead(true);
            n.setDtRead(OffsetDateTime.now());
        });
        notificationRepository.saveAll(unread);
    }

    // -------------------------------------------------------------------------
    //  Fetch + decrypt  (for REST GET /notifications)
    // -------------------------------------------------------------------------

    public List<NotificationDto> getForUser(UUID userId) {
        return notificationRepository.findUnreadByUser(userId).stream()
                .map(this::decrypt)
                .toList();
    }

    // -------------------------------------------------------------------------
    //  Internals
    // -------------------------------------------------------------------------

    /** Encrypt all sensitive fields and persist. */
    private Notification buildAndSave(UUID userId, NotifType type,
                                      String title, String body,
                                      Map<String, Object> payload) {
        return notificationRepository.save(
                Notification.builder()
                        .idUser(userId)
                        .tpNotif(type)
                        .dsTitle(encryptionService.encrypt(title))
                        .dsBody(encryptionService.encrypt(body))
                        .jsPayload(serializeAndEncrypt(payload))
                        .build()
        );
    }

    /** Decrypt a persisted Notification into a safe DTO for the wire. */
    private NotificationDto decrypt(Notification n) {
        String title   = quietDecrypt(n.getDsTitle());
        String body    = quietDecrypt(n.getDsBody());
        Map<String, Object> payload = decryptPayload(n.getJsPayload());

        return NotificationDto.builder()
                .id(n.getIdNotif())
                .type(n.getTpNotif())
                .title(title)
                .body(body)
                .payload(payload)
                .read(n.getStRead())
                .createdAt(n.getDtCreated())
                .build();
    }

    /**
     * Builds a wire-ready DTO directly from plaintext values we already have
     * in memory — avoids a pointless decrypt round-trip right after saving.
     */
    private NotificationDto toWireDto(Notification saved,
                                      String plainTitle,
                                      String plainBody,
                                      Map<String, Object> plainPayload) {
        return NotificationDto.builder()
                .id(saved.getIdNotif())
                .type(saved.getTpNotif())
                .title(plainTitle)
                .body(plainBody)
                .payload(plainPayload)
                .read(false)
                .createdAt(saved.getDtCreated())
                .build();
    }

    private String serializeAndEncrypt(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) return null;
        try {
            String json = objectMapper.writeValueAsString(payload);
            return encryptionService.encrypt(json);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize notification payload.", e);
        }
    }

    private Map<String, Object> decryptPayload(String encrypted) {
        if (encrypted == null) return Map.of();
        try {
            String json = encryptionService.decrypt(encrypted);
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Could not decrypt notification payload — treating as legacy plain JSON.");
            try {
                return objectMapper.readValue(encrypted, new TypeReference<>() {});
            } catch (Exception ex) {
                return Map.of();
            }
        }
    }

    /** Decrypt with a fallback for legacy plaintext rows already in the DB. */
    private String quietDecrypt(String value) {
        if (value == null) return null;
        try {
            return encryptionService.decrypt(value);
        } catch (Exception e) {
            log.warn("Notification field could not be decrypted — returning as-is (legacy row).");
            return value;
        }
    }
}