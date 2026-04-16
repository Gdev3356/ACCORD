package com.main.accord.domain.message;

import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.channel.ChReadState;
import com.main.accord.domain.channel.ChReadStateRepository;
import com.main.accord.domain.channel.Channel;
import com.main.accord.domain.channel.ChannelRepository;
import com.main.accord.domain.server.BanService;
import com.main.accord.domain.server.Member;
import com.main.accord.domain.server.MemberRepository;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import com.main.accord.domain.notification.NotificationService;
import com.main.accord.security.EncryptionService;
import com.main.accord.websocket.ChatHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository     messageRepository;
    private final ChannelRepository     channelRepository;
    private final EditHistoryRepository editHistoryRepository;
    private final PermissionService     permissionService;
    private final ChatHandler           chatHandler;
    private final EncryptionService encryptionService;
    private final MentionParser         mentionParser;
    private final NotificationService   notificationService;
    private final BanService            banService;
    private final MsAttachmentRepository msAttachmentRepository;
    private final ChReadStateRepository chReadStateRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public Message sendMessage(UUID channelId, UUID authorId, String content,
                               UUID replyToId, String tpMessage,
                               Map<String, Object> jsActivity) {
        if (banService.isPlatformBanned(authorId))
            throw new ForbiddenException("Your account has been suspended.");

        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found."));

        if (isUserTimedOut(authorId, channel.getIdServer())) {
            throw new ForbiddenException("You are currently timed out and cannot send messages.");
        }

        if (banService.isServerBanned(authorId, channel.getIdServer()))
            throw new ForbiddenException("You are banned from this server.");

        if (!permissionService.can(authorId, channelId, channel.getIdServer(), Permissions.SEND_MESSAGES))
            throw new ForbiddenException("You can't send messages here.");

        MentionParser.MentionResult mentions = mentionParser.parse(
                content != null ? content : "", authorId, channelId, channel.getIdServer()
        );

        String encryptedContent = content != null ? encryptionService.encrypt(mentions.sanitizedContent()) : null;

        Message saved = messageRepository.save(
                Message.builder()
                        .idChannel(channelId)
                        .idAuthor(authorId)
                        .idReplyTo(replyToId)
                        .dsContent(encryptedContent)
                        .tpMessage(tpMessage != null ? tpMessage : "text")
                        .jsActivity(jsActivity)
                        .build()
        );

        if (content != null && !content.isBlank())
            messageRepository.updateSearchVector(saved.getIdMessage(), mentions.sanitizedContent());

        Message broadcast = cloneWithDecryptedContent(saved, content);
        chatHandler.broadcastToChannel(channelId, broadcast);

        if (mentions.mentionedUserIds() != null && !mentions.mentionedUserIds().isEmpty()) {
            notificationService.dispatchMentionNotifications(
                    saved, mentions, channel.getIdServer(), authorId
            );
        }

        return broadcast;
    }

    @Transactional(readOnly = true)
    public List<Message> getMessages(UUID channelId, UUID requesterId, UUID beforeId, int limit) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found."));

        if (!permissionService.can(requesterId, channelId, channel.getIdServer(), Permissions.VIEW_CHANNELS))
            throw new ForbiddenException("You don't have access to this channel.");

        PageRequest page = PageRequest.of(0, Math.min(limit, 100));
        List<Message> messages = beforeId != null
                ? messageRepository.findBeforeMessage(channelId, beforeId, page)
                : messageRepository.findByChannel(channelId, page);

        // Decrypt into NEW objects — never mutate managed entities
        List<Message> result = messages.stream().map(m -> {
            if (m.getDsContent() == null) return m;
            try {
                Message copy = cloneWithDecryptedContent(m, encryptionService.decrypt(m.getDsContent()));
                return copy;
            } catch (Exception e) {
                return m; // legacy plaintext
            }
        }).toList();

        return result;
    }

    @Transactional
    public Message editMessage(UUID messageId, UUID editorId, String newContent) {
        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found."));

        if (!msg.getIdAuthor().equals(editorId)) {
            Channel channel = channelRepository.findById(msg.getIdChannel()).orElseThrow();
            if (!permissionService.can(editorId, msg.getIdChannel(), channel.getIdServer(), Permissions.MANAGE_MESSAGES)) {
                throw new ForbiddenException("You can't edit this message.");
            }
        }

        editHistoryRepository.save(
                EditHistory.builder()
                        .idMessage(messageId)
                        .dsContent(msg.getDsContent())
                        .build()
        );

        msg.setDsContent(encryptionService.encrypt(newContent));
        msg.setStEdited(true);
        Message saved = messageRepository.save(msg);

        Message broadcast = cloneWithDecryptedContent(saved, newContent);
        chatHandler.broadcastEditToChannel(msg.getIdChannel(), broadcast);
        return broadcast;
    }

    @Transactional
    public void markRead(UUID channelId, UUID userId, UUID lastMessageId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found."));
        if (!permissionService.can(userId, channelId, channel.getIdServer(), Permissions.VIEW_CHANNELS))
            throw new ForbiddenException("You don't have access to this channel.");

        if (lastMessageId != null && !messageRepository.existsById(lastMessageId)) {
            // Message not yet committed, skip or retry
            return;
        }
        ChReadState state = chReadStateRepository
                .findByIdChannelAndIdUser(channelId, userId)
                .orElse(ChReadState.builder().idChannel(channelId).idUser(userId).build());
        state.setIdLastReadMsg(lastMessageId);
        state.setDtLastRead(java.time.OffsetDateTime.now());
        chReadStateRepository.save(state);
    }

    public java.util.Map<UUID, Long> getUnreadCounts(UUID serverId, UUID userId) {
        return chReadStateRepository.countUnreadPerChannelInServer(serverId, userId)
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        row -> UUID.fromString(row[0].toString()),
                        row -> ((Number) row[1]).longValue()
                ));
    }

    @Transactional
    public void broadcastTyping(UUID channelId, UUID userId) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found."));
        if (!permissionService.can(userId, channelId, channel.getIdServer(), Permissions.VIEW_CHANNELS))
            throw new ForbiddenException("No access.");

        chatHandler.broadcastEventToChannel(channelId,
                new ChatHandler.ChatEvent("CHANNEL_TYPING", Map.of("userId", userId.toString())));
    }

    private boolean isUserTimedOut(UUID userId, UUID serverId) {
        Member member = memberRepository.findByIdServerAndIdUser(serverId, userId).orElse(null);
        if (member == null || !Boolean.TRUE.equals(member.getStTimeout())) {
            return false;
        }

        OffsetDateTime expires = member.getDtTimeoutExpires();
        if (expires != null && expires.isBefore(OffsetDateTime.now())) {
            // Auto-cleanup expired timeout (in case scheduled task hasn't run yet)
            member.setStTimeout(false);
            member.setDtTimeoutExpires(null);
            memberRepository.save(member);
            return false;
        }
        return true;
    }

    public List<Message> searchMessages(UUID channelId, UUID requesterId, String query, int limit) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found."));

        if (!permissionService.can(requesterId, channelId, channel.getIdServer(), Permissions.VIEW_CHANNELS))
            throw new ForbiddenException("You don't have access to this channel.");

        if (query == null || query.trim().isEmpty()) {
            return List.of();
        }

        List<Message> msgs = messageRepository.fullTextSearch(channelId, query, Math.min(limit, 100));

        // Decrypt each result — same pattern as getMessages()
        return msgs.stream().map(m -> {
            if (m.getDsContent() == null) return m;
            try {
                Message copy = cloneWithDecryptedContent(m, encryptionService.decrypt(m.getDsContent()));
                return copy;
            } catch (Exception e) {
                return m; // legacy plaintext
            }
        }).toList();
    }

    @Transactional
    public void deleteMessage(UUID messageId, UUID requesterId) {
        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found."));

        Channel channel = channelRepository.findById(msg.getIdChannel()).orElseThrow();
        boolean isAuthor = msg.getIdAuthor().equals(requesterId);
        boolean canManage = permissionService.can(
                requesterId, msg.getIdChannel(), channel.getIdServer(), Permissions.MANAGE_MESSAGES
        );

        // NEW: Check role hierarchy if not the author and not a manager
        if (!isAuthor && !canManage) {
            // Check if requester has higher role priority than message author
            if (!hasHigherRolePriority(requesterId, msg.getIdAuthor(), channel.getIdServer())) {
                throw new ForbiddenException("You cannot delete messages from users with equal or higher role.");
            }
        }

        if (!isAuthor && !canManage) {
            throw new ForbiddenException("You can't delete this message.");
        }

        msg.setStDeleted(true);
        msg.setDsContent(null);
        messageRepository.save(msg);
        chatHandler.broadcastDeleteToChannel(msg.getIdChannel(), messageId);
    }

    // Add this helper method
    private boolean hasHigherRolePriority(UUID requesterId, UUID targetId, UUID serverId) {
        short requesterTop = memberRepository.getHighestRolePosition(requesterId, serverId);
        short targetTop = memberRepository.getHighestRolePosition(targetId, serverId);

        // Requester needs STRICTLY HIGHER priority (LOWER number)
        return requesterTop < targetTop;
    }

    // Returns a transient copy with plain text content for broadcasting —
    // we never send encrypted content over the wire to clients
    private Message cloneWithDecryptedContent(Message source, String plainContent) {
        Message copy = new Message();
        copy.setIdMessage(source.getIdMessage());
        copy.setIdChannel(source.getIdChannel());
        copy.setIdAuthor(source.getIdAuthor());
        copy.setIdReplyTo(source.getIdReplyTo());
        copy.setDsContent(plainContent);
        copy.setStEdited(source.getStEdited());
        copy.setStPinned(source.getStPinned());
        copy.setStDeleted(source.getStDeleted());
        copy.setDtCreated(source.getDtCreated());
        copy.setDtEdited(source.getDtEdited());
        copy.setTpMessage(source.getTpMessage());
        copy.setJsActivity(source.getJsActivity());
        copy.setAttachments(source.getAttachments());
        return copy;
    }
}