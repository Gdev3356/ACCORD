package com.main.accord.domain.dm;

import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.account.AccountRepository;
import com.main.accord.domain.notification.NotifType;
import com.main.accord.domain.notification.NotificationService;
import com.main.accord.websocket.ChatHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public record SendMessageRequest(String content, UUID replyToId) {};
    private final NotificationService notificationService;
    private final AccountRepository accountRepository; // to resolve sender name

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

    public List<DmMessage> getMessages(UUID conversationId, UUID requesterId,
                                       UUID beforeId, int limit) {
        assertParticipant(conversationId, requesterId);
        PageRequest page = PageRequest.of(0, Math.min(limit, 100));
        return beforeId != null
                ? dmMessageRepository.findBeforeMessage(conversationId, beforeId, page)
                : dmMessageRepository.findByConversation(conversationId, page);
    }

    @Transactional
    public DmMessage sendMessage(UUID conversationId, UUID authorId, String content, UUID replyToId) {
        assertParticipant(conversationId, authorId);

        DmMessage saved = dmMessageRepository.save(
                DmMessage.builder()
                        .idConversation(conversationId)
                        .idAuthor(authorId)
                        .dsContent(content)
                        .idReplyTo(replyToId)
                        .build()
        );

        chatHandler.broadcastToDm(conversationId,
                Map.of("type", "DM_MESSAGE_CREATE", "data", saved));

        // Resolve sender name once
        String senderName = accountRepository.findById(authorId)
                .map(a -> a.getDsDisplayName())
                .orElse("Someone");

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

        return saved;
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
        return dmMessageRepository.searchContent(
                conversationId, query.toLowerCase(), PageRequest.of(0, Math.min(limit, 100))
        );
    }

    @Transactional
    public DmMessage editMessage(UUID messageId, UUID editorId, String newContent) {
        DmMessage msg = dmMessageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found."));

        if (!msg.getIdAuthor().equals(editorId)) {
            throw new ForbiddenException("You can only edit your own messages.");
        }

        assertParticipant(msg.getIdConversation(), editorId);

        msg.setDsContent(newContent);
        msg.setStEdited(true);
        msg.setDtEdited(java.time.OffsetDateTime.now());
        DmMessage saved = dmMessageRepository.save(msg);

        chatHandler.broadcastToDm(msg.getIdConversation(),
                Map.of("type", "DM_MESSAGE_EDIT", "data", saved));

        return saved;
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
        assertParticipant(msg.getIdConversation(), requesterId);
        return msg;
    }

}