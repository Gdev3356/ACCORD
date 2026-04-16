package com.main.accord.domain.webhook;

import com.main.accord.common.AccordException;
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
import com.main.accord.security.EncryptionService;
import com.main.accord.websocket.ChatHandler;
import com.main.accord.domain.account.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final EncryptionService encryptionService;

    // ──────────────────────────────────────────────────────────────────────
    // MEMBER JOIN (separate from leave)
    // ──────────────────────────────────────────────────────────────────────

    public void executeMemberJoinWebhook(UUID serverId, UUID userId,
                                         String userDisplayName, String userHandle, String pfpUrl) {
        List<Webhook> webhooks = webhookRepository.findByIdServerAndTpEventAndStActiveTrue(serverId, "MEMBER_JOIN");

        for (Webhook webhook : webhooks) {
            executeWebhook(webhook, Map.of(
                    "user", userDisplayName,
                    "user_handle", userHandle,
                    "user_id", userId.toString(),
                    "user_avatar", pfpUrl,
                    "server", getServerName(serverId)
            ));
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // MEMBER KICK
    // ──────────────────────────────────────────────────────────────────────

    public void executeMemberKickWebhook(UUID serverId, UUID targetId, UUID moderatorId,
                                         String targetName, String moderatorName, String reason) {
        List<Webhook> webhooks = webhookRepository.findByIdServerAndTpEventAndStActiveTrue(serverId, "MEMBER_KICK");

        for (Webhook webhook : webhooks) {
            executeWebhook(webhook, Map.of(
                    "user", targetName,
                    "moderator", moderatorName,
                    "server", getServerName(serverId),
                    "reason", reason != null ? reason : ""
            ));
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // MEMBER BAN
    // ──────────────────────────────────────────────────────────────────────

    public void executeMemberBanWebhook(UUID serverId, UUID targetId, UUID moderatorId,
                                        String targetName, String moderatorName, String reason) {
        List<Webhook> webhooks = webhookRepository.findByIdServerAndTpEventAndStActiveTrue(serverId, "MEMBER_BAN");

        for (Webhook webhook : webhooks) {
            executeWebhook(webhook, Map.of(
                    "user", targetName,
                    "moderator", moderatorName,
                    "server", getServerName(serverId),
                    "reason", reason != null ? reason : ""
            ));
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // MEMBER TIMEOUT
    // ──────────────────────────────────────────────────────────────────────

    public void executeMemberTimeoutWebhook(UUID serverId, UUID targetId, UUID moderatorId,
                                            String targetName, String moderatorName,
                                            int durationMinutes, String reason) {
        List<Webhook> webhooks = webhookRepository.findByIdServerAndTpEventAndStActiveTrue(serverId, "MEMBER_TIMEOUT");

        for (Webhook webhook : webhooks) {
            executeWebhook(webhook, Map.of(
                    "user", targetName,
                    "moderator", moderatorName,
                    "duration", String.valueOf(durationMinutes),
                    "server", getServerName(serverId),
                    "reason", reason != null ? reason : ""
            ));
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // ROLE ADD
    // ──────────────────────────────────────────────────────────────────────

    public void executeRoleAddWebhook(UUID serverId, UUID userId,
                                      String userDisplayName, String roleName) {
        List<Webhook> webhooks = webhookRepository.findByIdServerAndTpEventAndStActiveTrue(serverId, "ROLE_ADD");

        for (Webhook webhook : webhooks) {
            executeWebhook(webhook, Map.of(
                    "user", userDisplayName,
                    "role", roleName,
                    "server", getServerName(serverId)
            ));
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // ROLE REMOVE
    // ──────────────────────────────────────────────────────────────────────

    public void executeRoleRemoveWebhook(UUID serverId, UUID userId,
                                         String userDisplayName, String roleName) {
        List<Webhook> webhooks = webhookRepository.findByIdServerAndTpEventAndStActiveTrue(serverId, "ROLE_REMOVE");

        for (Webhook webhook : webhooks) {
            executeWebhook(webhook, Map.of(
                    "user", userDisplayName,
                    "role", roleName,
                    "server", getServerName(serverId)
            ));
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helper Methods
    // ──────────────────────────────────────────────────────────────────────

    private void executeWebhook(Webhook webhook, Map<String, String> variables) {
        try {
            String content = interpolateMessage(webhook.getDsMessageTemplate(), variables);

            Message saved = messageRepository.save(
                    Message.builder()
                            .idChannel(webhook.getIdChannel())
                            .idAuthor(null)
                            .dsContent(encryptionService.encrypt(content))
                            .tpMessage("webhook")
                            .jsActivity(Map.of(  // ← Store webhook metadata
                                    "webhookName", webhook.getDsName(),
                                    "webhookAvatar", webhook.getDsAvatarUrl(),
                                    "webhookBio", webhook.getDsBio(),
                                    "webhookBanner", webhook.getDsBannerUrl(),
                                    "webhookColor", webhook.getNrColor()
                            ))
                            .build()
            );

            chatHandler.broadcastToChannel(webhook.getIdChannel(), saved);

        } catch (Exception e) {
            throw new AccordException("Not a valid message");
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

    public void executeMemberLeaveWebhooks(UUID serverId, UUID userId,
                                           String userDisplayName, String userHandle) { // ← add handle
        List<Webhook> webhooks = webhookRepository
                .findByIdServerAndTpEventAndStActiveTrue(serverId, "MEMBER_JOIN");

        for (Webhook webhook : webhooks) {
            executeWebhook(webhook, Map.of(
                    "user",        userDisplayName,
                    "user_handle", userHandle,      // ← add
                    "user_id",     userId.toString(),
                    "server",      getServerName(serverId),
                    "action",      "left"
            ));
        }
    }

    public List<Webhook> getServerWebhooks(UUID serverId, UUID requesterId) {
        if (!permissionService.can(requesterId, null, serverId, Permissions.MANAGE_SERVER)) {
            throw new ForbiddenException("You need MANAGE_SERVER permission to view webhooks.");
        }
        return webhookRepository.findByIdServerAndStActiveTrue(serverId);
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
    public Webhook createWebhook(UUID serverId, UUID channelId, UUID creatorId,
                                 String name, String avatarUrl, String bio,
                                 String bannerUrl, Integer color, String eventType,
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
                .dsBio(bio)
                .dsBannerUrl(bannerUrl)
                .nrColor(color)
                .tpEvent(eventType)
                .dsMessageTemplate(messageTemplate)
                .stActive(true)
                .build();

        return webhookRepository.save(webhook);
    }
}