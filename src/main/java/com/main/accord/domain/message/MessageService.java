package com.main.accord.domain.message;

import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.channel.Channel;
import com.main.accord.domain.channel.ChannelRepository;
import com.main.accord.domain.server.BanService;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import com.main.accord.domain.notification.NotificationService;
import com.main.accord.security.EncryptionService;
import com.main.accord.websocket.ChatHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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

    @Transactional
    public Message sendMessage(UUID channelId, UUID authorId, String content) {
        // Platform ban check — rejected before anything else
        if (banService.isPlatformBanned(authorId)) {
            throw new ForbiddenException("Your account has been suspended.");
        }

        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found."));

        // Server ban check
        if (banService.isServerBanned(authorId, channel.getIdServer())) {
            throw new ForbiddenException("You are banned from this server.");
        }

        if (!permissionService.can(authorId, channelId, channel.getIdServer(), Permissions.SEND_MESSAGES)) {
            throw new ForbiddenException("You can't send messages here.");
        }

        // Parse and resolve mentions
        MentionParser.MentionResult mentions = mentionParser.parse(
                content, authorId, channelId, channel.getIdServer()
        );

        Message saved = messageRepository.save(
                Message.builder()
                        .idChannel(channelId)
                        .idAuthor(authorId)
                        .dsContent(encryptionService.encrypt(mentions.sanitizedContent()))
                        .build()
        );

        messageRepository.updateSearchVector(saved.getIdMessage(), mentions.sanitizedContent());

        // Broadcast the message — send decrypted content over WS, not the ciphertext
        Message broadcast = cloneWithDecryptedContent(saved, mentions.sanitizedContent());
        chatHandler.broadcastToChannel(channelId, broadcast);

        // Fire mention notifications asynchronously
        if (!mentions.mentionedUserIds().isEmpty()) {
            notificationService.dispatchMentionNotifications(
                    saved, mentions, channel.getIdServer(), authorId
            );
        }

        return broadcast;
    }

    public List<Message> getMessages(UUID channelId, UUID requesterId, UUID beforeId, int limit) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found."));

        if (!permissionService.can(requesterId, channelId, channel.getIdServer(), Permissions.VIEW_CHANNELS)) {
            throw new ForbiddenException("You don't have access to this channel.");
        }

        PageRequest page = PageRequest.of(0, Math.min(limit, 100));
        List<Message> messages = beforeId != null
                ? messageRepository.findBeforeMessage(channelId, beforeId, page)
                : messageRepository.findByChannel(channelId, page);

        messages.forEach(m -> {
            if (m.getDsContent() != null) {
                m.setDsContent(encryptionService.decrypt(m.getDsContent()));
            }
        });
        return messages;
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
    public void deleteMessage(UUID messageId, UUID requesterId) {
        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found."));

        Channel channel = channelRepository.findById(msg.getIdChannel()).orElseThrow();
        boolean isAuthor  = msg.getIdAuthor().equals(requesterId);
        boolean canManage = permissionService.can(
                requesterId, msg.getIdChannel(), channel.getIdServer(), Permissions.MANAGE_MESSAGES
        );

        if (!isAuthor && !canManage) throw new ForbiddenException("You can't delete this message.");

        msg.setStDeleted(true);
        msg.setDsContent(null);
        messageRepository.save(msg);
        chatHandler.broadcastDeleteToChannel(msg.getIdChannel(), messageId);
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
        return copy;
    }
}