package com.main.accord.domain.webhook;

import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.channel.Channel;
import com.main.accord.domain.channel.ChannelRepository;
import com.main.accord.domain.message.Message;
import com.main.accord.domain.message.MessageRepository;
import com.main.accord.domain.server.MemberRepository;
import com.main.accord.domain.server.Server;
import com.main.accord.domain.server.ServerRepository;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import com.main.accord.websocket.ChatHandler;
import com.main.accord.domain.account.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final WebhookRepository webhookRepository;
    private final ChannelRepository channelRepository;
    private final ServerRepository serverRepository;
    private final MemberRepository memberRepository;
    private final PermissionService permissionService;
    private final MessageRepository messageRepository;
    private final ChatHandler chatHandler;
    private final AccountRepository accountRepository;

    @Transactional
    public Webhook createWebhook(UUID serverId, UUID channelId, UUID creatorId,
                                 String name, String avatarUrl, String eventType,
                                 String messageTemplate) {
        // Check permission
        if (!permissionService.can(creatorId, null, serverId, Permissions.MANAGE_SERVER)) {
            throw new ForbiddenException("You need MANAGE_SERVER permission to create webhooks.");
        }

        // Verify channel exists and belongs to server
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new NotFoundException("Channel not found."));

        if (!channel.getIdServer().equals(serverId)) {
            throw new ForbiddenException("Channel does not belong to this server.");
        }

        Webhook webhook = Webhook.builder()
                .idServer(serverId)
                .idChannel(channelId)
                .dsName(name)
                .dsAvatarUrl(avatarUrl)
                .tpEvent(eventType)
                .dsMessageTemplate(messageTemplate)
                .stActive(true)
                .build();

        return webhookRepository.save(webhook);
    }

    @Transactional
    public void deleteWebhook(UUID webhookId, UUID userId) {
        Webhook webhook = webhookRepository.findById(webhookId)
                .orElseThrow(() -> new NotFoundException("Webhook not found."));

        if (!permissionService.can(userId, null, webhook.getIdServer(), Permissions.MANAGE_SERVER)) {
            throw new ForbiddenException("You need MANAGE_SERVER permission to delete webhooks.");
        }

        webhookRepository.delete(webhook);
    }

    @Transactional
    public void executeMemberJoinWebhooks(UUID serverId, UUID userId, String userDisplayName, String userHandle) {
        List<Webhook> webhooks = webhookRepository.findByIdServerAndTpEventAndStActiveTrue(serverId, "MEMBER_JOIN");

        for (Webhook webhook : webhooks) {
            executeWebhook(webhook, Map.of(
                    "user", userDisplayName,
                    "user_handle", userHandle,
                    "user_id", userId.toString(),
                    "server", getServerName(serverId)
            ));
        }
    }

    @Transactional
    public void executeDmStartWebhook(UUID conversationId, UUID userAId, UUID userBId,
                                      String userADisplayName, String userBDisplayName) {
        // Find servers where BOTH users are members
        List<UUID> commonServerIds = memberRepository.findCommonServers(userAId, userBId);

        if (commonServerIds.isEmpty()) return;

        // Get active DM_START webhooks ONLY in those common servers
        List<Webhook> webhooks = webhookRepository.findByServerIdsAndEventAndActiveTrue(commonServerIds, "DM_START");

        for (Webhook webhook : webhooks) {
            executeWebhook(webhook, Map.of(
                    "user1", userADisplayName,
                    "user2", userBDisplayName,
                    "user1_id", userAId.toString(),
                    "user2_id", userBId.toString(),
                    "conversation_id", conversationId.toString()
            ));
        }
    }

    private void executeWebhook(Webhook webhook, Map<String, String> variables) {
        try {
            String content = interpolateMessage(webhook.getDsMessageTemplate(), variables);

            Message saved = messageRepository.save(
                    Message.builder()
                            .idChannel(webhook.getIdChannel())
                            .idAuthor(null)          // system/webhook
                            .dsContent(content)      // no encryption needed for system msgs, or encrypt here
                            .tpMessage("webhook")
                            .build()
            );

            chatHandler.broadcastToChannel(webhook.getIdChannel(), saved);
            log.debug("Webhook executed in channel {}: {}", webhook.getIdChannel(), content);

        } catch (Exception e) {
            log.error("Failed to execute webhook {}: {}", webhook.getIdWebhook(), e.getMessage());
        }
    }

    private String interpolateMessage(String template, Map<String, String> variables) {
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String getServerName(UUID serverId) {
        return serverRepository.findById(serverId)
                .map(Server::getDsName)
                .orElse("Unknown Server");
    }

    public List<Webhook> getServerWebhooks(UUID serverId, UUID requesterId) {
        if (!permissionService.can(requesterId, null, serverId, Permissions.MANAGE_SERVER)) {
            throw new ForbiddenException("You need MANAGE_SERVER permission to view webhooks.");
        }
        return webhookRepository.findByIdServerAndStActiveTrue(serverId);
    }

    @Transactional
    public Webhook updateWebhook(UUID webhookId, UUID userId, WebhookController.UpdateWebhookRequest req) {
        Webhook webhook = webhookRepository.findById(webhookId)
                .orElseThrow(() -> new NotFoundException("Webhook not found."));

        if (!permissionService.can(userId, null, webhook.getIdServer(), Permissions.MANAGE_SERVER)) {
            throw new ForbiddenException("You need MANAGE_SERVER permission to update webhooks.");
        }

        if (req.name() != null) {
            webhook.setDsName(req.name());
        }
        if (req.channelId() != null) {
            Channel channel = channelRepository.findById(req.channelId())
                    .orElseThrow(() -> new NotFoundException("Channel not found."));
            if (!channel.getIdServer().equals(webhook.getIdServer())) {
                throw new ForbiddenException("Channel does not belong to this server.");
            }
            webhook.setIdChannel(req.channelId());
        }
        if (req.avatarUrl() != null) {
            webhook.setDsAvatarUrl(req.avatarUrl());
        }
        if (req.eventType() != null) {
            webhook.setTpEvent(req.eventType());
        }
        if (req.messageTemplate() != null) {
            webhook.setDsMessageTemplate(req.messageTemplate());
        }
        if (req.active() != null) {
            webhook.setStActive(req.active());
        }

        return webhookRepository.save(webhook);
    }

    public void executeMemberLeaveWebhooks(UUID serverId, UUID userId, String userDisplayName) {
        List<Webhook> webhooks = webhookRepository.findByIdServerAndTpEventAndStActiveTrue(serverId, "MEMBER_JOIN");

        for (Webhook webhook : webhooks) {
            executeWebhook(webhook, Map.of(
                    "user", userDisplayName,
                    "server", getServerName(serverId),
                    "action", "left"
            ));
        }
    }

    public void executeMemberModerationWebhook(UUID serverId, UUID targetId, UUID moderatorId,
                                               String targetName, String moderatorName,
                                               String action, String reason) {
        List<Webhook> webhooks = webhookRepository.findByIdServerAndTpEventAndStActiveTrue(serverId, "MEMBER_MODERATION");

        String reasonText = (reason != null && !reason.isEmpty()) ? " Reason: " + reason : "";

        for (Webhook webhook : webhooks) {
            executeWebhook(webhook, Map.of(
                    "user", targetName,
                    "moderator", moderatorName,
                    "server", getServerName(serverId),
                    "action", action,
                    "reason", reasonText
            ));
        }
    }

    public void executeMemberRoleWebhook(UUID serverId, UUID userId, String userDisplayName,
                                         String roleName, String action) {
        List<Webhook> webhooks = webhookRepository.findByIdServerAndTpEventAndStActiveTrue(serverId, "MEMBER_ROLE");

        for (Webhook webhook : webhooks) {
            executeWebhook(webhook, Map.of(
                    "user", userDisplayName,
                    "role", roleName,
                    "server", getServerName(serverId),
                    "action", action
            ));
        }
    }
}