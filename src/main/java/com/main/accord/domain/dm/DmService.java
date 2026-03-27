package com.main.accord.domain.dm;

import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.account.Account;
import com.main.accord.domain.account.AccountRepository;
import com.main.accord.domain.account.Visuals;
import com.main.accord.domain.account.VisualsRepository;
import com.main.accord.domain.notification.NotifType;
import com.main.accord.domain.notification.NotificationService;
import com.main.accord.security.EncryptionService;
import com.main.accord.websocket.ChatHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DmService {

    private final ConversationRepository  conversationRepository;
    private final ParticipantRepository   participantRepository;
    private final DmMessageRepository     dmMessageRepository;
    private final FriendshipRepository    friendshipRepository;
    private final ChatHandler             chatHandler;
    private final DmReadStateRepository dmReadStateRepository;
    public record SendMessageRequest(String content, UUID replyToId, UUID forwardAttachmentFrom) {}
    private final NotificationService notificationService;
    private final AccountRepository accountRepository; // to resolve sender name
    private final VisualsRepository visualsRepository;
    private final DmAttachmentRepository dmAttachmentRepository;
    private final EncryptionService encryptionService;

    public List<Conversation> getConversations(UUID userId) {
        return conversationRepository.findAllByParticipant(userId);
    }

    @Transactional
    public Conversation openDm(UUID requesterId, UUID targetId) {
        return conversationRepository
                .findDirectBetween(requesterId, targetId)
                .orElseGet(() -> {
                    Conversation convo = conversationRepository.save(
                            Conversation.builder().stGroup(false).build()
                    );
                    participantRepository.saveAll(List.of(
                            Participant.builder()
                                    .idConversation(convo.getIdConversation())
                                    .idUser(requesterId).build(),
                            Participant.builder()
                                    .idConversation(convo.getIdConversation())
                                    .idUser(targetId).build()
                    ));
                    return convo;
                });
    }

    @Transactional
    public Conversation createGroup(UUID creatorId, List<UUID> userIds, String name) {
        Conversation convo = conversationRepository.save(
                Conversation.builder()
                        .stGroup(true)
                        .idOwner(creatorId)
                        .dsName(name)
                        .build()
        );
        // Add creator + all invited users
        List<Participant> participants = new java.util.ArrayList<>();
        participants.add(Participant.builder()
                .idConversation(convo.getIdConversation())
                .idUser(creatorId).build());
        for (UUID uid : userIds) {
            if (!uid.equals(creatorId)) {
                participants.add(Participant.builder()
                        .idConversation(convo.getIdConversation())
                        .idUser(uid).build());
            }
        }
        participantRepository.saveAll(participants);
        return convo;
    }

    private void populateForwardedFrom(List<DmMessage> messages) {
        messages.stream()
                .filter(m -> m.getIdForwardedFrom() != null)
                .forEach(m -> dmMessageRepository.findById(m.getIdForwardedFrom())
                        .ifPresent(original -> {
                            // Decrypt the original's content for the DTO preview
                            String plainContent = original.getDsContent() != null
                                    ? encryptionService.decrypt(original.getDsContent())
                                    : null;
                            String name = original.getIdAuthor() != null
                                    ? accountRepository.findById(original.getIdAuthor())
                                    .map(Account::getDsDisplayName).orElse("User")
                                    : "User";
                            m.setForwardedFrom(new DmMessage.ForwardedFromDto(
                                    original.getIdMessage(),
                                    original.getIdAuthor(),
                                    name
                            ));
                        }));
    }

    // Single message variant
    private void populateForwardedFrom(DmMessage msg) {
        populateForwardedFrom(List.of(msg));
    }

    public List<DmMessage> getMessages(UUID conversationId, UUID requesterId,
                                       UUID beforeId, int limit) {
        assertParticipant(conversationId, requesterId);
        PageRequest page = PageRequest.of(0, Math.min(limit, 100));
        List<DmMessage> msgs = beforeId != null
                ? dmMessageRepository.findBeforeMessage(conversationId, beforeId, page)
                : dmMessageRepository.findByConversation(conversationId, page);

        decryptAll(msgs);
        populateForwardedFrom(msgs);
        return msgs;
    }

    @Transactional
    public DmMessage sendMessage(UUID conversationId, UUID authorId,
                                 String content, UUID replyToId, UUID forwardAttachmentFrom) {
        assertParticipant(conversationId, authorId);

        // Encrypt before persisting
        String encryptedContent = content != null ? encryptionService.encrypt(content) : null;

        DmMessage saved = dmMessageRepository.save(
                DmMessage.builder()
                        .idConversation(conversationId)
                        .idAuthor(authorId)
                        .dsContent(encryptedContent)
                        .idReplyTo(replyToId)
                        .idForwardedFrom(forwardAttachmentFrom)
                        .build()
        );

        // Write plaintext into the search vector — content is never stored as plaintext
        if (content != null && !content.isBlank()) {
            dmMessageRepository.updateSearchVector(saved.getIdMessage(), content);
        }

        // ── Clone attachments from forwarded message ───────────────────────
        if (forwardAttachmentFrom != null) {
            List<DmAttachment> originals = dmAttachmentRepository.findByIdMessage(forwardAttachmentFrom);
            if (!originals.isEmpty()) {
                List<DmAttachment> clones = originals.stream()
                        .map(att -> DmAttachment.builder()
                                .idMessage(saved.getIdMessage())
                                .dsUrl(att.getDsUrl())
                                .dsFilename(att.getDsFilename())
                                .dsMimeType(att.getDsMimeType())
                                .nrSizeBytes(att.getNrSizeBytes())
                                .nrWidth(att.getNrWidth())
                                .nrHeight(att.getNrHeight())
                                .build())
                        .toList();
                dmAttachmentRepository.saveAll(clones);
                saved.setAttachments(clones);
            }
        }

        // Broadcast with PLAIN text — never send ciphertext to clients
        DmMessage broadcast = cloneWithDecryptedContent(saved, content);
        chatHandler.broadcastToDm(conversationId,
                Map.of("type", "DM_MESSAGE_CREATE", "data", broadcast));

        // Notifications use plaintext content
        String senderName = accountRepository.findById(authorId)
                .map(Account::getDsDisplayName).orElse("Someone");

        // ── Reply notification ─────────────────────────────────────────────
        if (replyToId != null) {
            dmMessageRepository.findById(replyToId).ifPresent(parent -> {
                if (parent.getIdAuthor() != null && !parent.getIdAuthor().equals(authorId)) {
                    notificationService.send(
                            parent.getIdAuthor(),
                            NotifType.message,
                            senderName + " replied to you",
                            content != null ? content : "📎 Attachment",
                            Map.of("conversationId", conversationId.toString(),
                                    "messageId",      saved.getIdMessage().toString())
                    );
                }
            });
        }

        // ── Mention notifications ──────────────────────────────────────────
        if (content != null && content.contains("@")) {
            participantRepository.findByIdConversationAndDtLeftIsNull(conversationId)
                    .stream()
                    .filter(p -> !p.getIdUser().equals(authorId))
                    .forEach(p -> accountRepository.findById(p.getIdUser()).ifPresent(acc -> {
                        if (content.toLowerCase().contains("@" + acc.getDsHandle().toLowerCase())) {
                            notificationService.send(
                                    p.getIdUser(),
                                    NotifType.mention,
                                    "You were mentioned by " + senderName,
                                    content,
                                    Map.of("conversationId", conversationId.toString(),
                                            "messageId",      saved.getIdMessage().toString())
                            );
                        }
                    }));
        }

        return broadcast;
    }

    public List<Participant> getParticipants(UUID conversationId, UUID requesterId) {
        assertParticipant(conversationId, requesterId);
        return participantRepository.findByIdConversationAndDtLeftIsNull(conversationId);
    }

    private void assertParticipant(UUID conversationId, UUID userId) {
        if (!participantRepository.isActiveParticipant(conversationId, userId)) {
            throw new ForbiddenException("You are not part of this conversation.");
        }
    }

    public void broadcastTyping(UUID conversationId, UUID userId) {
        assertParticipant(conversationId, userId);
        chatHandler.broadcastToDm(conversationId,
                Map.of("type", "DM_TYPING", "data", Map.of("userId", userId.toString())));
    }

    public List<DmMessage> searchMessages(UUID conversationId, UUID requesterId,
                                          String query, int limit) {
        assertParticipant(conversationId, requesterId);

        List<DmMessage> msgs = dmMessageRepository.fullTextSearch(
                conversationId, query, Math.min(limit, 100)
        );

        decryptAll(msgs);            // decrypt dsContent before returning
        populateForwardedFrom(msgs);
        return msgs;
    }

    @Transactional
    public DmMessage editMessage(UUID messageId, UUID editorId, String newContent) {
        DmMessage msg = dmMessageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found."));

        if (!msg.getIdAuthor().equals(editorId))
            throw new ForbiddenException("You can only edit your own messages.");

        if (msg.getDtCreated() != null &&
                msg.getDtCreated().isBefore(OffsetDateTime.now().minusHours(3)))
            throw new ForbiddenException("Messages can only be edited within 3 hours of sending.");

        assertParticipant(msg.getIdConversation(), editorId);

        msg.setDsContent(encryptionService.encrypt(newContent));   // ← encrypt
        msg.setStEdited(true);
        msg.setDtEdited(OffsetDateTime.now());
        DmMessage saved = dmMessageRepository.save(msg);

        if (newContent != null && !newContent.isBlank()) {
            dmMessageRepository.updateSearchVector(messageId, newContent);
        }

        DmMessage broadcast = cloneWithDecryptedContent(saved, newContent);
        chatHandler.broadcastToDm(msg.getIdConversation(),
                Map.of("type", "DM_MESSAGE_EDIT", "data", broadcast));

        return broadcast;
    }

    @Transactional
    public void markRead(UUID conversationId, UUID userId, UUID lastMessageId) {
        assertParticipant(conversationId, userId);
        DmReadState state = dmReadStateRepository
                .findByIdConversationAndIdUser(conversationId, userId)
                .orElse(DmReadState.builder()
                        .idConversation(conversationId)
                        .idUser(userId)
                        .build());
        state.setIdLastReadMsg(lastMessageId);
        state.setDtLastRead(java.time.OffsetDateTime.now());
        dmReadStateRepository.save(state);
    }

    @Transactional
    public void setMuted(UUID conversationId, UUID userId, boolean muted) {
        assertParticipant(conversationId, userId);
        DmReadState state = dmReadStateRepository
                .findByIdConversationAndIdUser(conversationId, userId)
                .orElse(DmReadState.builder()
                        .idConversation(conversationId)
                        .idUser(userId)
                        .build());
        state.setStMuted(muted);
        dmReadStateRepository.save(state);
    }

    public List<UUID> getUnreadConversations(UUID userId) {
        return dmReadStateRepository.findConversationsWithUnread(userId);
    }

    public DmMessage getMessage(UUID messageId, UUID requesterId) {
        DmMessage msg = dmMessageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found."));
        decrypt(msg);
        populateForwardedFrom(msg);
        assertParticipant(msg.getIdConversation(), requesterId);
        return msg;
    }


    public List<ConversationSummaryDto> getConversationSummaries(UUID userId) {
        List<Conversation> convos = conversationRepository.findAllByParticipant(userId); // ← fix 1

        return convos.stream().map(c -> {
            if (Boolean.TRUE.equals(c.getStGroup())) { // ← fix 2
                return ConversationSummaryDto.builder()
                        .idConversation(c.getIdConversation())
                        .stGroup(true)
                        .dsName(c.getDsName())
                        .build();
            }

            UUID otherId = participantRepository.findOtherParticipant(c.getIdConversation(), userId);
            if (otherId == null) {
                return ConversationSummaryDto.builder()
                        .idConversation(c.getIdConversation())
                        .stGroup(false)
                        .build();
            }

            Account other   = accountRepository.findById(otherId).orElse(null);
            Visuals visuals = visualsRepository.findById(otherId).orElse(null);

            boolean isFriend = friendshipRepository.findBetween(userId, otherId)
                    .map(f -> f.getStStatus() == FriendStatus.accepted)
                    .orElse(false);

            DmReadState readState = dmReadStateRepository
                    .findByIdConversationAndIdUser(c.getIdConversation(), userId)
                    .orElse(null);

            int unread = 0;
            if (readState != null && readState.getDtLastRead() != null) {
                unread = (int) dmMessageRepository.countUnreadSince(
                        c.getIdConversation(), readState.getDtLastRead()
                );
            } else if (readState == null) {
                // Never opened — count everything
                unread = (int) dmMessageRepository.countUnreadSince(
                        c.getIdConversation(), OffsetDateTime.parse("1970-01-01T00:00:00Z")
                );
            }

            return ConversationSummaryDto.builder()
                    .idConversation(c.getIdConversation())
                    .stGroup(false)
                    .otherId(otherId)
                    .otherDisplayName(other != null ? other.getDsDisplayName() : "User")
                    .otherPfpUrl(visuals != null ? visuals.getDsPfpUrl() : null)
                    .otherPresence(other != null ? other.getStPresence().name() : "offline")
                    .isFriend(isFriend)
                    .nrUnread(unread)
                    .build();
        }).toList();
    }

    @Transactional
    public void deleteMessage(UUID messageId, UUID requesterId) {
        DmMessage msg = dmMessageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found."));

        if (!msg.getIdAuthor().equals(requesterId))
            throw new ForbiddenException("You can only delete your own messages.");

        assertParticipant(msg.getIdConversation(), requesterId);

        msg.setStDeleted(true);
        msg.setDsContent(null);
        dmMessageRepository.save(msg);

        chatHandler.broadcastToDm(msg.getIdConversation(),
                Map.of("type", "DM_MESSAGE_DELETE", "data", Map.of("messageId", messageId)));
    }

    private DmMessage cloneWithDecryptedContent(DmMessage source, String plainContent) {
        DmMessage copy = new DmMessage();
        copy.setIdMessage(source.getIdMessage());
        copy.setIdConversation(source.getIdConversation());
        copy.setIdAuthor(source.getIdAuthor());
        copy.setIdReplyTo(source.getIdReplyTo());
        copy.setIdForwardedFrom(source.getIdForwardedFrom());
        copy.setDsContent(plainContent);
        copy.setStEdited(source.getStEdited());
        copy.setStDeleted(source.getStDeleted());
        copy.setDtCreated(source.getDtCreated());
        copy.setDtEdited(source.getDtEdited());
        copy.setAttachments(source.getAttachments());
        copy.setForwardedFrom(source.getForwardedFrom());
        return copy;
    }

    private void decrypt(DmMessage msg) {
        if (msg.getDsContent() == null) return;
        try {
            msg.setDsContent(encryptionService.decrypt(msg.getDsContent()));
        } catch (Exception e) {
            // Message predates encryption — content is already plaintext, leave it as-is
        }
    }

    private void decryptAll(List<DmMessage> msgs) {
        msgs.forEach(this::decrypt);
    }
}