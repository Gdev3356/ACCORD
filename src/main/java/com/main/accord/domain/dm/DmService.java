package com.main.accord.domain.dm;

import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
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

    /** Open or retrieve a 1-on-1 DM with another user. */
    @Transactional
    public Conversation openDm(UUID requesterId, UUID targetId) {
        // TODO: check friendship status (optional — you may allow DMs without being friends)
        return conversationRepository
                .findDirectBetween(requesterId, targetId)
                .orElseGet(() -> {
                    Conversation convo = conversationRepository.save(
                            Conversation.builder().stGroup(false).build()
                    );
                    participantRepository.saveAll(List.of(
                            Participant.builder().idConversation(convo.getIdConversation()).idUser(requesterId).build(),
                            Participant.builder().idConversation(convo.getIdConversation()).idUser(targetId).build()
                    ));
                    return convo;
                });
    }

    public List<DmMessage> getMessages(UUID conversationId, UUID requesterId, UUID beforeId, int limit) {
        assertParticipant(conversationId, requesterId);
        PageRequest page = PageRequest.of(0, Math.min(limit, 100));
        return beforeId != null
                ? dmMessageRepository.findBeforeMessage(conversationId, beforeId, page)
                : dmMessageRepository.findByConversation(conversationId, page);
    }

    @Transactional
    public DmMessage sendMessage(UUID conversationId, UUID authorId, String content) {
        assertParticipant(conversationId, authorId);
        DmMessage saved = dmMessageRepository.save(
                DmMessage.builder()
                        .idConversation(conversationId)
                        .idAuthor(authorId)
                        .dsContent(content)
                        .build()
        );
        chatHandler.broadcastToDm(conversationId,
                Map.of("type", "MESSAGE_CREATE", "data", saved));
        return saved;
    }

    private void assertParticipant(UUID conversationId, UUID userId) {
        if (!participantRepository.isActiveParticipant(conversationId, userId)) {
            throw new ForbiddenException("You are not part of this conversation.");
        }
    }
}