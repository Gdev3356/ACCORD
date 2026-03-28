package com.main.accord.domain.dm;

import com.main.accord.common.AccordException;
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
        // 1. Collect all unique IDs of forwarded messages
        List<UUID> forwardedIds = messages.stream()
                .map(DmMessage::getIdForwardedFrom)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        if (forwardedIds.isEmpty()) return;

        // 2. Batch fetch original messages
        Map<UUID, DmMessage> originalsMap = dmMessageRepository.findAllById(forwardedIds).stream()
                .collect(java.util.stream.Collectors.toMap(DmMessage::getIdMessage, m -> m));

        // 3. FIX: Use the correct ID getter for the Account entity
        List<UUID> authorIds = originalsMap.values().stream()
                .map(DmMessage::getIdAuthor)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, String> authorNamesMap = accountRepository.findAllById(authorIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        Account::getIdUser, // Changed from getIdAccount to getIdUser
                        Account::getDsDisplayName,
                        (existing, replacement) -> existing // Handle duplicates just in case
                ));

        // 4. Map back to DTOs
        messages.stream()
                .filter(m -> m.getIdForwardedFrom() != null)
                .forEach(m -> {
                    DmMessage original = originalsMap.get(m.getIdForwardedFrom());
                    if (original != null) {

                        decrypt(original);
                        String plainContent = original.getDsContent();

                        // Fallback to "User" if author name is missing
                        String name = authorNamesMap.getOrDefault(original.getIdAuthor(), "User");

                        m.setForwardedFrom(new DmMessage.ForwardedFromDto(
                                original.getIdMessage(),
                                original.getIdAuthor(),
                                name
                        ));
                    }
                });
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

        // Security check: ensure they are still in the room
        assertParticipant(msg.getIdConversation(), editorId);

        if (msg.getDtCreated() != null &&
                msg.getDtCreated().isBefore(OffsetDateTime.now().minusHours(3)))
            throw new ForbiddenException("Messages can only be edited within 3 hours.");

        // Encrypt the new content for storage
        String encrypted = newContent != null ? encryptionService.encrypt(newContent) : null;
        msg.setDsContent(encrypted);
        msg.setStEdited(true);
        msg.setDtEdited(OffsetDateTime.now());

        dmMessageRepository.save(msg);

        if (newContent != null && !newContent.isBlank()) {
            dmMessageRepository.updateSearchVector(messageId, newContent);
        }

        // Use the helper to ensure consistent decryption/fallback for the broadcast
        DmMessage broadcast = cloneWithDecryptedContent(msg, newContent);
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
        List<Conversation> convos = conversationRepository.findAllByParticipant(userId);

        List<UUID> directConvIds = convos.stream()
                .filter(c -> !Boolean.TRUE.equals(c.getStGroup()))
                .map(Conversation::getIdConversation)
                .toList();

        // ── 1 query: all other participant IDs ────────────────────────────────
        Map<UUID, UUID> convToOther = participantRepository
                .findOtherParticipantsIn(directConvIds, userId).stream()
                .collect(java.util.stream.Collectors.toMap(
                        Participant::getIdConversation,
                        Participant::getIdUser,
                        (a, b) -> a
                ));

        List<UUID> otherIds = convToOther.values().stream().distinct().toList();

        // ── 1 query each: accounts, visuals, friendships ──────────────────────
        Map<UUID, Account> accounts = accountRepository.findAllById(otherIds).stream()
                .collect(java.util.stream.Collectors.toMap(Account::getIdUser, a -> a));

        Map<UUID, Visuals> visualsMap = visualsRepository.findAllById(otherIds).stream()
                .collect(java.util.stream.Collectors.toMap(Visuals::getIdUser, v -> v));

        java.util.Set<UUID> friendIds = (otherIds.isEmpty()
                ? java.util.List.<Friendship>of()
                : friendshipRepository.findAcceptedWithAny(userId, otherIds)).stream()
                .map(f -> f.getIdUserA().equals(userId) ? f.getIdUserB() : f.getIdUserA())
                .collect(java.util.stream.Collectors.toSet());

        // ── 1 query: unread counts ────────────────────────────────────────────
        Map<UUID, Long> unreadCounts = (directConvIds.isEmpty()
                ? java.util.List.<Object[]>of()
                : dmMessageRepository.countUnreadPerConversation(userId, directConvIds)).stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> UUID.fromString(row[0].toString()),
                        row -> ((Number) row[1]).longValue()
                ));

        // ── Assemble ──────────────────────────────────────────────────────────
        return convos.stream().map(c -> {
            if (Boolean.TRUE.equals(c.getStGroup())) {
                return ConversationSummaryDto.builder()
                        .idConversation(c.getIdConversation())
                        .stGroup(true)
                        .dsName(c.getDsName())
                        .build();
            }

            UUID otherId = convToOther.get(c.getIdConversation());
            if (otherId == null) {
                return ConversationSummaryDto.builder()
                        .idConversation(c.getIdConversation())
                        .stGroup(false)
                        .build();
            }

            Account other  = accounts.get(otherId);
            Visuals vis    = visualsMap.get(otherId);

            return ConversationSummaryDto.builder()
                    .idConversation(c.getIdConversation())
                    .stGroup(false)
                    .otherId(otherId)
                    .otherDisplayName(other != null ? other.getDsDisplayName() : "User")
                    .otherPfpUrl(vis != null ? vis.getDsPfpUrl() : null)
                    .otherPresence(other != null ? other.getStPresence().name() : "offline")
                    .isFriend(friendIds.contains(otherId))
                    .nrUnread(unreadCounts.getOrDefault(c.getIdConversation(), 0L).intValue())
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

    public void broadcastAttachmentUpdate(UUID messageId) {
        dmMessageRepository.findById(messageId).ifPresent(msg -> {
            List<DmAttachment> attachments = dmAttachmentRepository.findByIdMessage(messageId);
            msg.setAttachments(attachments);
            // Reuse the existing decrypt path — identical to getMessages/getMessage
            decrypt(msg);
            populateForwardedFrom(msg);
            chatHandler.broadcastToDm(
                    msg.getIdConversation(),
                    Map.of("type", "DM_MESSAGE_EDIT", "data", msg)
            );
        });
    }

    @Transactional
    public void addMember(UUID conversationId, UUID requesterId, UUID targetId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found."));
        if (!Boolean.TRUE.equals(conv.getStGroup()))
            throw new ForbiddenException("This is not a group conversation.");
        if (!conv.getIdOwner().equals(requesterId))
            throw new ForbiddenException("Only the group owner can add members.");

        long current = participantRepository
                .findByIdConversationAndDtLeftIsNull(conversationId).size();
        if (current >= 10)
            throw new AccordException("Groups are limited to 10 members.");

        boolean alreadyIn = participantRepository
                .findByIdConversationAndDtLeftIsNull(conversationId)
                .stream().anyMatch(p -> p.getIdUser().equals(targetId));
        if (alreadyIn)
            throw new AccordException("User is already in this group.");

        participantRepository.save(Participant.builder()
                .idConversation(conversationId)
                .idUser(targetId)
                .build());

        chatHandler.broadcastToDm(conversationId, Map.of(
                "type", "GROUP_MEMBER_ADD",
                "data", Map.of("conversationId", conversationId, "userId", targetId)
        ));
    }

    @Transactional
    public void removeMember(UUID conversationId, UUID requesterId, UUID targetId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found."));
        if (!Boolean.TRUE.equals(conv.getStGroup()))
            throw new ForbiddenException("This is not a group conversation.");
        if (!conv.getIdOwner().equals(requesterId))
            throw new ForbiddenException("Only the group owner can remove members.");
        if (requesterId.equals(targetId))
            throw new ForbiddenException("Use the leave endpoint to leave a group.");

        participantRepository.findByIdConversationAndDtLeftIsNull(conversationId)
                .stream()
                .filter(p -> p.getIdUser().equals(targetId))
                .findFirst()
                .ifPresent(p -> {
                    p.setDtLeft(OffsetDateTime.now());
                    participantRepository.save(p);
                });

        chatHandler.broadcastToDm(conversationId, Map.of(
                "type", "GROUP_MEMBER_REMOVE",
                "data", Map.of("conversationId", conversationId, "userId", targetId)
        ));
    }

    @Transactional
    public void leaveGroup(UUID conversationId, UUID userId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found."));
        if (!Boolean.TRUE.equals(conv.getStGroup()))
            throw new ForbiddenException("This is not a group conversation.");

        assertParticipant(conversationId, userId);

        participantRepository.findByIdConversationAndDtLeftIsNull(conversationId)
                .stream()
                .filter(p -> p.getIdUser().equals(userId))
                .findFirst()
                .ifPresent(p -> {
                    p.setDtLeft(OffsetDateTime.now());
                    participantRepository.save(p);
                });

        // If owner left, transfer to the next member or leave ownerless
        if (conv.getIdOwner().equals(userId)) {
            participantRepository.findByIdConversationAndDtLeftIsNull(conversationId)
                    .stream()
                    .map(Participant::getIdUser)
                    .findFirst()
                    .ifPresent(newOwner -> {
                        conv.setIdOwner(newOwner);
                        conversationRepository.save(conv);
                    });
        }

        chatHandler.broadcastToDm(conversationId, Map.of(
                "type", "GROUP_MEMBER_REMOVE",
                "data", Map.of("conversationId", conversationId, "userId", userId)
        ));
    }

    @Transactional
    public Conversation renameGroup(UUID conversationId, UUID requesterId, String name) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found."));
        if (!Boolean.TRUE.equals(conv.getStGroup()))
            throw new ForbiddenException("This is not a group conversation.");
        if (!conv.getIdOwner().equals(requesterId))
            throw new ForbiddenException("Only the group owner can rename the group.");

        conv.setDsName(name);
        conversationRepository.save(conv);

        chatHandler.broadcastToDm(conversationId, Map.of(
                "type", "GROUP_RENAMED",
                "data", Map.of("conversationId", conversationId, "dsName", name)
        ));
        return conv;
    }
}