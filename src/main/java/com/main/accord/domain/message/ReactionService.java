package com.main.accord.domain.message;

import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.account.AccountRepository;
import com.main.accord.domain.account.VisualsRepository;
import com.main.accord.domain.channel.ChannelRepository;
import com.main.accord.domain.dm.DmMessage;
import com.main.accord.domain.dm.DmMessageRepository;
import com.main.accord.domain.dm.DmReaction;
import com.main.accord.domain.dm.DmReactionRepository;
import com.main.accord.domain.notification.NotifType;
import com.main.accord.domain.notification.NotificationService;
import com.main.accord.domain.server.MemberRepository;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import com.main.accord.websocket.ChatHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
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
    private final MemberRepository     memberRepository;
    private final AccountRepository    accountRepository;
    private final VisualsRepository    visualsRepository;

    // ── DM Reactions ──────────────────────────────────────────────────────────

    public List<ReactionSummary> getDmReactions(UUID messageId, UUID callerId) {
        return dmReactionRepository
                .countByEmojiForUserBatch(List.of(messageId), callerId)
                .stream()
                .filter(row -> messageId.equals(row.getMessageId()))
                .map(row -> new ReactionSummary(
                        row.getEmoji(),
                        row.getCount(),
                        Boolean.TRUE.equals(row.getReactedByMe()),
                        new ArrayList<>()
                ))
                .toList();
    }

    public Map<UUID, List<ReactionSummary>> getDmReactionsBatch(List<UUID> messageIds, UUID callerId) {
        Map<UUID, List<ReactionSummary>> result = new LinkedHashMap<>();
        messageIds.forEach(id -> result.put(id, new ArrayList<>()));
        if (messageIds.isEmpty()) return result;

        dmReactionRepository.countByEmojiForUserBatch(messageIds, callerId)
                .forEach(row -> result
                        .get(row.getMessageId())
                        .add(new ReactionSummary(
                                row.getEmoji(),
                                row.getCount(),
                                Boolean.TRUE.equals(row.getReactedByMe()),
                                new ArrayList<>()
                        ))
                );

        return result;
    }

    @Transactional
    public void addDmReaction(UUID messageId, UUID userId, String emoji) {
        DmMessage msg = dmMessageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found."));

        var account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User account not found"));
        var visuals = visualsRepository.findById(userId).orElse(null);
        String pfpUrl = visuals != null ? visuals.getDsPfpUrl() : "https://i.imgur.com/eTh2muI.png";

        if (!dmReactionRepository.existsByIdMessageAndIdUserAndDsEmoji(messageId, userId, emoji)) {
            dmReactionRepository.save(DmReaction.builder()
                    .idMessage(messageId)
                    .idUser(userId)
                    .dsEmoji(emoji)
                    .build());
        }

        if (msg.getIdAuthor() != null && !msg.getIdAuthor().equals(userId)) {
            try {
                notificationService.send(msg.getIdAuthor(), NotifType.message,
                        "New reaction on your message", emoji,
                        Map.of("conversationId", msg.getIdConversation().toString(),
                                "messageId", messageId.toString()));
            } catch (Exception e) {
                log.warn("Failed to send DM reaction notification: {}", e.getMessage());
            }
        }

        chatHandler.broadcastToDm(msg.getIdConversation(), Map.of(
                "type", "DM_REACTION_ADD",
                "data", Map.of(
                        "messageId", messageId,
                        "userId",    userId,
                        "emoji",     emoji,
                        "reactor",   Map.of(
                                "idUser",      userId,
                                "displayName", account.getDsDisplayName(),
                                "pfpUrl",      pfpUrl
                        )
                )
        ));
    }

    @Transactional
    public void removeDmReaction(UUID messageId, UUID userId, String emoji) {
        dmReactionRepository.deleteByIdMessageAndIdUserAndDsEmoji(messageId, userId, emoji);
        dmMessageRepository.findById(messageId).ifPresent(msg ->
                chatHandler.broadcastToDm(msg.getIdConversation(), Map.of(
                        "type", "DM_REACTION_REMOVE",
                        "data", Map.of("messageId", messageId, "userId", userId, "emoji", emoji)
                ))
        );
    }

    // ── Server Channel Reactions ──────────────────────────────────────────────

    public List<ReactionSummary> getServerReactions(UUID messageId, UUID callerId) {
        return reactionRepository.countByEmojiForUser(messageId, callerId)
                .stream()
                .map(row -> new ReactionSummary(
                        row.getEmoji(),
                        row.getCount(),
                        Boolean.TRUE.equals(row.getReactedByMe()),
                        new ArrayList<>()
                ))
                .toList();
    }

    public Map<UUID, List<ReactionSummary>> getServerReactionsBatch(List<UUID> messageIds, UUID callerId) {
        Map<UUID, List<ReactionSummary>> result = new LinkedHashMap<>();
        messageIds.forEach(id -> result.put(id, new ArrayList<>()));
        if (messageIds.isEmpty()) return result;

        reactionRepository.countByEmojiForUserBatch(messageIds, callerId)
                .forEach(row -> result
                        .get(row.getMessageId())
                        .add(new ReactionSummary(
                                row.getEmoji(),
                                row.getCount(),
                                Boolean.TRUE.equals(row.getReactedByMe()),
                                new ArrayList<>()
                        ))
                );

        return result;
    }

    @Transactional
    public void addServerReaction(UUID messageId, UUID channelId, UUID userId, String emoji) {
        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found."));

        UUID actualChannelId = msg.getIdChannel();
        var channel = channelRepository.findById(actualChannelId)
                .orElseThrow(() -> new NotFoundException("Channel not found."));

        var member = memberRepository.findByIdServerAndIdUser(channel.getIdServer(), userId)
                .orElseThrow(() -> new ForbiddenException("User is not a member of this server"));
        var account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User account not found"));
        var visuals = visualsRepository.findById(userId).orElse(null);
        String pfpUrl = visuals != null ? visuals.getDsPfpUrl() : "https://i.imgur.com/eTh2muI.png";

        if (!permissionService.can(userId, actualChannelId, channel.getIdServer(), Permissions.VIEW_CHANNELS))
            throw new ForbiddenException("You don't have access to this channel.");

        if (!reactionRepository.existsByIdMessageAndIdUserAndDsEmoji(messageId, userId, emoji)) {
            reactionRepository.save(Reaction.builder()
                    .idMessage(messageId)
                    .idUser(userId)
                    .dsEmoji(emoji)
                    .build());
        }

        if (msg.getIdAuthor() != null && !msg.getIdAuthor().equals(userId)) {
            try {
                notificationService.send(msg.getIdAuthor(), NotifType.message,
                        "New reaction on your message", emoji,
                        Map.of("channelId", actualChannelId.toString(),
                                "messageId", messageId.toString()));
            } catch (Exception e) {
                log.warn("Failed to send server reaction notification: {}", e.getMessage());
            }
        }

        String displayNickname = member.getDsNickname() != null
                ? member.getDsNickname()
                : account.getDsDisplayName();

        chatHandler.broadcastEventToChannel(actualChannelId,
                new ChatHandler.ChatEvent("MESSAGE_REACTION_ADD", Map.of(
                        "messageId", messageId,
                        "userId",    userId,
                        "emoji",     emoji,
                        "reactor",   Map.of(
                                "idUser",      userId,
                                "displayName", account.getDsDisplayName(),
                                "nickname",    displayNickname,
                                "pfpUrl",      pfpUrl
                        )
                ))
        );
    }

    @Transactional
    public void removeServerReaction(UUID messageId, UUID channelId, UUID userId, String emoji) {
        reactionRepository.deleteByIdMessageAndIdUserAndDsEmoji(messageId, userId, emoji);
        chatHandler.broadcastToChannel(channelId, Map.of(
                "type", "MESSAGE_REACTION_REMOVE",
                "data", Map.of("messageId", messageId, "userId", userId, "emoji", emoji)
        ));
    }

    // ── Legacy compatibility ──────────────────────────────────────────────────

    /** @deprecated Use {@link #getDmReactions(UUID, UUID)} */
    @Deprecated public List<ReactionSummary> getReactions(UUID messageId, UUID callerId) {
        return getDmReactions(messageId, callerId);
    }

    /** @deprecated Use {@link #getDmReactionsBatch(List, UUID)} */
    @Deprecated public Map<UUID, List<ReactionSummary>> getReactionsBatch(List<UUID> messageIds, UUID callerId) {
        return getDmReactionsBatch(messageIds, callerId);
    }

    /** @deprecated Use {@link #addDmReaction(UUID, UUID, String)} */
    @Deprecated @Transactional public void addReaction(UUID messageId, UUID userId, String emoji) {
        addDmReaction(messageId, userId, emoji);
    }

    /** @deprecated Use {@link #removeDmReaction(UUID, UUID, String)} */
    @Deprecated @Transactional public void removeReaction(UUID messageId, UUID userId, String emoji) {
        removeDmReaction(messageId, userId, emoji);
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record ReactionSummary(
            String        emoji,
            long          count,
            boolean       reactedByMe,
            List<ReactorDto> reactors
    ) {
        public ReactionSummary(String emoji, long count, boolean reactedByMe) {
            this(emoji, count, reactedByMe, new ArrayList<>());
        }
    }

    public record ReactorDto(UUID idUser, String displayName, String nickname, String pfpUrl) {}
}