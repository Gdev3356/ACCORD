package com.main.accord.domain.message;

import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.channel.ChannelRepository;
import com.main.accord.domain.dm.DmMessage;
import com.main.accord.domain.dm.DmMessageRepository;
import com.main.accord.domain.dm.DmReaction;
import com.main.accord.domain.dm.DmReactionRepository;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import com.main.accord.websocket.ChatHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionRepository   reactionRepository;
    private final MessageRepository    messageRepository;
    private final ChannelRepository    channelRepository;
    private final PermissionService    permissionService;
    private final DmReactionRepository dmReactionRepository;
    private final DmMessageRepository dmMessageRepository;
    private final ChatHandler          chatHandler;

    public List<ReactionSummary> getReactions(UUID messageId, UUID callerId) {
        return reactionRepository.countByEmojiForUser(messageId, callerId).stream()
                .map(row -> new ReactionSummary(
                        (String)  row[0],
                        (Long)    row[1],
                        (Boolean) row[2]))
                .toList();
    }

    public Map<UUID, List<ReactionSummary>> getReactionsBatch(List<UUID> messageIds, UUID callerId) {
        // Pre-populate every requested ID with an empty list so missing keys don't cause
        // frontend null-checks to fail
        Map<UUID, List<ReactionSummary>> result = new LinkedHashMap<>();
        messageIds.forEach(id -> result.put(id, new java.util.ArrayList<>()));

        if (messageIds.isEmpty()) return result;

        reactionRepository.countByEmojiForUserBatch(messageIds, callerId)
                .forEach(row -> {
                    UUID    msgId      = (UUID)   row[0];
                    String  emoji      = (String) row[1];
                    long    count      = ((Number) row[2]).longValue();
                    boolean reactedByMe = row[3] != null && ((Number) row[3]).longValue() > 0;
                    result.get(msgId).add(new ReactionSummary(emoji, count, reactedByMe));
                });

        return result;
    }

    @Transactional
    public void addReaction(UUID messageId, UUID userId, String emoji) {
        DmMessage msg = dmMessageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found."));

        if (!dmReactionRepository.existsByIdMessageAndIdUserAndDsEmoji(messageId, userId, emoji)) {
            dmReactionRepository.save(DmReaction.builder()
                    .idMessage(messageId)
                    .idUser(userId)
                    .dsEmoji(emoji)
                    .build());
        }

        chatHandler.broadcastToDm(msg.getIdConversation(),
                Map.of("type", "DM_REACTION_ADD",
                        "data", Map.of("messageId", messageId, "userId", userId, "emoji", emoji)));
    }

    @Transactional
    public void removeReaction(UUID messageId, UUID userId, String emoji) {
        dmReactionRepository.deleteByIdMessageAndIdUserAndDsEmoji(messageId, userId, emoji);

        dmMessageRepository.findById(messageId).ifPresent(msg ->
                chatHandler.broadcastToDm(msg.getIdConversation(),
                        Map.of("type", "DM_REACTION_REMOVE",
                                "data", Map.of("messageId", messageId, "userId", userId, "emoji", emoji)))
        );
    }

    public record ReactionSummary(String emoji, long count, boolean reactedByMe) {}
}