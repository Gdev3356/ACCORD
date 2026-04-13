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

    // ── DM reactions (unchanged) ───────────────────────────────────────────────

    public List<ReactionSummary> getReactions(UUID messageId, UUID callerId) {
        return reactionRepository.countByEmojiForUser(messageId, callerId).stream()
                .map(row -> new ReactionSummary((String) row[0], (Long) row[1], (Boolean) row[2]))
                .toList();
    }

    public Map<UUID, List<ReactionSummary>> getReactionsBatch(List<UUID> messageIds, UUID callerId) {
        Map<UUID, List<ReactionSummary>> result = new LinkedHashMap<>();
        messageIds.forEach(id -> result.put(id, new ArrayList<>()));
        if (messageIds.isEmpty()) return result;

        reactionRepository.countByEmojiForUserBatch(messageIds, callerId).forEach(row -> {
            UUID    msgId       = (UUID)   row[0];
            String  emoji       = (String) row[1];
            long    count       = ((Number) row[2]).longValue();
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
                    .idMessage(messageId).idUser(userId).dsEmoji(emoji).build());
        }

        if (msg.getIdAuthor() != null && !msg.getIdAuthor().equals(userId)) {
            notificationService.send(msg.getIdAuthor(), NotifType.message,
                    "New reaction on your message", emoji,
                    Map.of("conversationId", msg.getIdConversation().toString(),
                            "messageId",      messageId.toString()));
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
                                "data", Map.of("messageId", messageId,
                                        "userId",    userId,
                                        "emoji",     emoji))));
    }

    // ── Server channel reactions ───────────────────────────────────────────────

    public List<ReactionSummary> getServerReactions(UUID messageId, UUID callerId) {
        return reactionRepository.countByEmojiForUser(messageId, callerId).stream()
                .map(row -> new ReactionSummary((String) row[0], (Long) row[1], (Boolean) row[2]))
                .toList();
    }

    public Map<UUID, List<ReactionSummary>> getServerReactionsBatch(List<UUID> messageIds, UUID callerId) {
        Map<UUID, List<ReactionSummary>> result = new LinkedHashMap<>();
        messageIds.forEach(id -> result.put(id, new ArrayList<>()));
        if (messageIds.isEmpty()) return result;

        reactionRepository.countByEmojiForUserBatch(messageIds, callerId).forEach(row -> {
            UUID    msgId       = (UUID)   row[0];
            String  emoji       = (String) row[1];
            long    count       = ((Number) row[2]).longValue();
            boolean reactedByMe = row[3] != null && ((Number) row[3]).longValue() > 0;
            result.get(msgId).add(new ReactionSummary(emoji, count, reactedByMe));
        });
        return result;
    }

    @Transactional
    public void addServerReaction(UUID messageId, UUID channelId, UUID userId, String emoji) {
        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found."));

        var channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found."));

        if (!permissionService.can(userId, channelId, channel.getIdServer(), Permissions.VIEW_CHANNELS)) {
            throw new ForbiddenException("You don't have access to this channel.");
        }

        if (!reactionRepository.existsByIdMessageAndIdUserAndDsEmoji(messageId, userId, emoji)) {
            reactionRepository.save(Reaction.builder()
                    .idMessage(messageId).idUser(userId).dsEmoji(emoji).build());
        }

        if (msg.getIdAuthor() != null && !msg.getIdAuthor().equals(userId)) {
            notificationService.send(msg.getIdAuthor(), NotifType.message,
                    "New reaction on your message", emoji,
                    Map.of("channelId", channelId.toString(),
                            "messageId", messageId.toString()));
        }

        chatHandler.broadcastToChannel(channelId,
                Map.of("type", "MESSAGE_REACTION_ADD",
                        "data", Map.of("messageId", messageId,
                                "userId",    userId,
                                "emoji",     emoji)));
    }

    @Transactional
    public void removeServerReaction(UUID messageId, UUID channelId, UUID userId, String emoji) {
        reactionRepository.deleteByIdMessageAndIdUserAndDsEmoji(messageId, userId, emoji);
        chatHandler.broadcastToChannel(channelId,
                Map.of("type", "MESSAGE_REACTION_REMOVE",
                        "data", Map.of("messageId", messageId,
                                "userId",    userId,
                                "emoji",     emoji)));
    }

    public record ReactionSummary(String emoji, long count, boolean reactedByMe) {}
}