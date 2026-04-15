package com.main.accord.domain.message;

import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.channel.ChannelRepository;
import com.main.accord.domain.dm.DmMessage;
import com.main.accord.domain.dm.DmMessageRepository;
import com.main.accord.domain.dm.DmReaction;
import com.main.accord.domain.dm.DmReactionRepository;
import com.main.accord.domain.notification.NotifType;
import com.main.accord.domain.notification.NotificationService;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import com.main.accord.websocket.ChatHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ReactionService {

    private final ReactionRepository   reactionRepository;
    private final MessageRepository    messageRepository;
    private final ChannelRepository    channelRepository;
    private final PermissionService    permissionService;
    private final DmReactionRepository dmReactionRepository;
    private final DmMessageRepository  dmMessageRepository;
    private final ChatHandler          chatHandler;
    private final NotificationService  notificationService;

    // ──────────────────────────────────────────────────────────────────────
    // DM REACTIONS
    // ──────────────────────────────────────────────────────────────────────

    public List<ReactionSummary> getDmReactions(UUID messageId, UUID callerId) {
        List<Object[]> results = dmReactionRepository.countByEmojiForUserBatch(
                List.of(messageId), callerId);

        return results.stream()
                .filter(row -> messageId.equals(row[0]))
                .map(row -> new ReactionSummary(
                        (String) row[1],                    // emoji
                        ((Number) row[2]).longValue(),      // count
                        ((Number) row[3]).longValue() > 0   // reactedByMe
                ))
                .toList();
    }

    public Map<UUID, List<ReactionSummary>> getDmReactionsBatch(List<UUID> messageIds, UUID callerId) {
        Map<UUID, List<ReactionSummary>> result = new LinkedHashMap<>();
        messageIds.forEach(id -> result.put(id, new ArrayList<>()));

        if (messageIds.isEmpty()) return result;

        List<Object[]> rows = dmReactionRepository.countByEmojiForUserBatch(messageIds, callerId);

        for (Object[] row : rows) {
            UUID msgId = (UUID) row[0];
            String emoji = (String) row[1];
            long count = ((Number) row[2]).longValue();
            boolean reactedByMe = ((Number) row[3]).longValue() > 0;

            result.get(msgId).add(new ReactionSummary(emoji, count, reactedByMe));
        }

        return result;
    }

    @Transactional
    public void addDmReaction(UUID messageId, UUID userId, String emoji) {
        DmMessage msg = dmMessageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found."));

        if (!dmReactionRepository.existsByIdMessageAndIdUserAndDsEmoji(messageId, userId, emoji)) {
            dmReactionRepository.save(DmReaction.builder()
                    .idMessage(messageId)
                    .idUser(userId)
                    .dsEmoji(emoji)
                    .build());
        }

        // Send notification if reacting to someone else's message
        if (msg.getIdAuthor() != null && !msg.getIdAuthor().equals(userId)) {
            notificationService.send(
                    msg.getIdAuthor(),
                    NotifType.message,
                    "New reaction on your message",
                    emoji,
                    Map.of(
                            "conversationId", msg.getIdConversation().toString(),
                            "messageId", messageId.toString()
                    )
            );
        }

        // Broadcast to DM conversation
        chatHandler.broadcastToDm(
                msg.getIdConversation(),
                Map.of(
                        "type", "DM_REACTION_ADD",
                        "data", Map.of(
                                "messageId", messageId,
                                "userId", userId,
                                "emoji", emoji
                        )
                )
        );
    }

    @Transactional
    public void removeDmReaction(UUID messageId, UUID userId, String emoji) {
        dmReactionRepository.deleteByIdMessageAndIdUserAndDsEmoji(messageId, userId, emoji);

        dmMessageRepository.findById(messageId).ifPresent(msg ->
                chatHandler.broadcastToDm(
                        msg.getIdConversation(),
                        Map.of(
                                "type", "DM_REACTION_REMOVE",
                                "data", Map.of(
                                        "messageId", messageId,
                                        "userId", userId,
                                        "emoji", emoji
                                )
                        )
                )
        );
    }

    // ──────────────────────────────────────────────────────────────────────
    // SERVER CHANNEL REACTIONS
    // ──────────────────────────────────────────────────────────────────────

    public List<ReactionSummary> getServerReactions(UUID messageId, UUID callerId) {
        return reactionRepository.countByEmojiForUser(messageId, callerId).stream()
                .map(row -> new ReactionSummary(
                        (String) row[0],
                        (Long) row[1],
                        (Boolean) row[2]
                ))
                .toList();
    }

    public Map<UUID, List<ReactionSummary>> getServerReactionsBatch(List<UUID> messageIds, UUID callerId) {
        Map<UUID, List<ReactionSummary>> result = new LinkedHashMap<>();
        messageIds.forEach(id -> result.put(id, new ArrayList<>()));

        if (messageIds.isEmpty()) return result;

        List<Object[]> rows = reactionRepository.countByEmojiForUserBatch(messageIds, callerId);

        for (Object[] row : rows) {
            UUID msgId = (UUID) row[0];
            String emoji = (String) row[1];
            long count = ((Number) row[2]).longValue();
            boolean reactedByMe = row[3] != null && ((Number) row[3]).longValue() > 0;

            result.get(msgId).add(new ReactionSummary(emoji, count, reactedByMe));
        }

        return result;
    }

    @Transactional
    public void addServerReaction(UUID messageId, UUID channelId, UUID userId, String emoji) {
        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found."));

        var channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found."));

        // Check permissions
        if (!permissionService.can(userId, channelId, channel.getIdServer(), Permissions.VIEW_CHANNELS)) {
            throw new ForbiddenException("You don't have access to this channel.");
        }

        // Add reaction if it doesn't exist
        if (!reactionRepository.existsByIdMessageAndIdUserAndDsEmoji(messageId, userId, emoji)) {
            reactionRepository.save(Reaction.builder()
                    .idMessage(messageId)
                    .idUser(userId)
                    .dsEmoji(emoji)
                    .build());
        }

        // Send notification if reacting to someone else's message
        if (msg.getIdAuthor() != null && !msg.getIdAuthor().equals(userId)) {
            notificationService.send(
                    msg.getIdAuthor(),
                    NotifType.message,
                    "New reaction on your message",
                    emoji,
                    Map.of(
                            "channelId", channelId.toString(),
                            "messageId", messageId.toString()
                    )
            );
        }

        // Broadcast to channel
        chatHandler.broadcastToChannel(
                channelId,
                Map.of(
                        "type", "MESSAGE_REACTION_ADD",
                        "data", Map.of(
                                "messageId", messageId,
                                "userId", userId,
                                "emoji", emoji
                        )
                )
        );
    }

    @Transactional
    public void removeServerReaction(UUID messageId, UUID channelId, UUID userId, String emoji) {
        reactionRepository.deleteByIdMessageAndIdUserAndDsEmoji(messageId, userId, emoji);

        chatHandler.broadcastToChannel(
                channelId,
                Map.of(
                        "type", "MESSAGE_REACTION_REMOVE",
                        "data", Map.of(
                                "messageId", messageId,
                                "userId", userId,
                                "emoji", emoji
                        )
                )
        );
    }

    // ──────────────────────────────────────────────────────────────────────
    // Legacy compatibility methods (keep existing controller working)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * @deprecated Use {@link #getDmReactions(UUID, UUID)} instead
     */
    @Deprecated
    public List<ReactionSummary> getReactions(UUID messageId, UUID callerId) {
        return getDmReactions(messageId, callerId);
    }

    /**
     * @deprecated Use {@link #getDmReactionsBatch(List, UUID)} instead
     */
    @Deprecated
    public Map<UUID, List<ReactionSummary>> getReactionsBatch(List<UUID> messageIds, UUID callerId) {
        return getDmReactionsBatch(messageIds, callerId);
    }

    /**
     * @deprecated Use {@link #addDmReaction(UUID, UUID, String)} instead
     */
    @Deprecated
    @Transactional
    public void addReaction(UUID messageId, UUID userId, String emoji) {
        addDmReaction(messageId, userId, emoji);
    }

    /**
     * @deprecated Use {@link #removeDmReaction(UUID, UUID, String)} instead
     */
    @Deprecated
    @Transactional
    public void removeReaction(UUID messageId, UUID userId, String emoji) {
        removeDmReaction(messageId, userId, emoji);
    }

    // ──────────────────────────────────────────────────────────────────────
    // DTO
    // ──────────────────────────────────────────────────────────────────────

    public record ReactionSummary(String emoji, long count, boolean reactedByMe) {}
}