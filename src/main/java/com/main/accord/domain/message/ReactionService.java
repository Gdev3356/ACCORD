package com.main.accord.domain.message;

import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.channel.ChannelRepository;
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

        reactionRepository.countByEmojiForUserBatch(messageIds, callerId)
                .forEach(row -> {
                    UUID   msgId      = (UUID)    row[0];
                    String emoji      = (String)  row[1];
                    long   count      = (Long)    row[2];
                    boolean reactedByMe = (Boolean) row[3];
                    result.get(msgId).add(new ReactionSummary(emoji, count, reactedByMe));
                });

        return result;
    }

    @Transactional
    public void addReaction(UUID messageId, UUID userId, String emoji) {
        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found."));

        var channel = channelRepository.findById(msg.getIdChannel()).orElseThrow();
        if (!permissionService.can(userId, msg.getIdChannel(), channel.getIdServer(), Permissions.VIEW_CHANNELS))
            throw new ForbiddenException("You can't react here.");

        if (!reactionRepository.existsByIdMessageAndIdUserAndDsEmoji(messageId, userId, emoji)) {
            reactionRepository.save(Reaction.builder()
                    .idMessage(messageId)
                    .idUser(userId)
                    .dsEmoji(emoji)
                    .build());
        }

        chatHandler.broadcastToChannel(msg.getIdChannel(),
                Map.of("type", "DM_REACTION_ADD",
                        "data", Map.of("messageId", messageId, "userId", userId, "emoji", emoji)));
    }

    @Transactional
    public void removeReaction(UUID messageId, UUID userId, String emoji) {
        reactionRepository.deleteByIdMessageAndIdUserAndDsEmoji(messageId, userId, emoji);

        messageRepository.findById(messageId).ifPresent(msg ->
                chatHandler.broadcastToChannel(msg.getIdChannel(),
                        Map.of("type", "DM_REACTION_REMOVE",
                                "data", Map.of("messageId", messageId, "userId", userId, "emoji", emoji)))
        );
    }

    public record ReactionSummary(String emoji, long count, boolean reactedByMe) {}
}