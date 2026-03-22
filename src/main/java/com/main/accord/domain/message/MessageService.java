package com.main.accord.domain.message;

import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.channel.Channel;
import com.main.accord.domain.channel.ChannelRepository;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import com.main.accord.websocket.ChatHandler;
import com.main.accord.security.EncryptionService;
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
    private final EncryptionService     encryptionService;  // ← injected, not nested

    @Transactional
    public Message sendMessage(UUID channelId, UUID authorId, String content) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found"));

        if (!permissionService.can(authorId, channelId, channel.getIdServer(), Permissions.SEND_MESSAGES)) {
            throw new ForbiddenException("You can't send messages here.");
        }

        Message saved = messageRepository.save(
                Message.builder()
                        .idChannel(channelId)
                        .idAuthor(authorId)
                        .dsContent(encryptionService.encrypt(content))  // ← encrypt before save
                        .build()
        );

        chatHandler.broadcastToChannel(channelId, saved);
        return saved;
    }

    public List<Message> getMessages(UUID channelId, UUID requesterId, UUID beforeId, int limit) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found"));

        if (!permissionService.can(requesterId, channelId, channel.getIdServer(), Permissions.VIEW_CHANNELS)) {
            throw new ForbiddenException("You don't have access to this channel.");
        }

        PageRequest page = PageRequest.of(0, Math.min(limit, 100));
        List<Message> messages = beforeId != null
                ? messageRepository.findBeforeMessage(channelId, beforeId, page)
                : messageRepository.findByChannel(channelId, page);

        // Decrypt each message before returning to client
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
                .orElseThrow(() -> new NotFoundException("Message not found"));

        if (!msg.getIdAuthor().equals(editorId)) {
            Channel channel = channelRepository.findById(msg.getIdChannel()).orElseThrow();
            if (!permissionService.can(editorId, msg.getIdChannel(), channel.getIdServer(), Permissions.MANAGE_MESSAGES)) {
                throw new ForbiddenException("You can't edit this message.");
            }
        }

        // Snapshot raw (still encrypted) content before overwriting
        editHistoryRepository.save(
                EditHistory.builder()
                        .idMessage(messageId)
                        .dsContent(msg.getDsContent())  // stored encrypted
                        .build()
        );

        msg.setDsContent(encryptionService.encrypt(newContent));
        msg.setStEdited(true);
        Message saved = messageRepository.save(msg);
        chatHandler.broadcastEditToChannel(msg.getIdChannel(), saved);
        return saved;
    }

    @Transactional
    public void deleteMessage(UUID messageId, UUID requesterId) {
        Message msg = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found"));

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
}