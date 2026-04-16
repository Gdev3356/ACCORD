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
    private final EncryptionService encryptionService;

    // ──────────────────────────────────────────────────────────────────────
    // EXECUTE WEBHOOKS BY EVENT TYPE
    // ──────────────────────────────────────────────────────────────────────

    public void executeMemberJoinWebhook(UUID serverId, UUID userId,
                                         String userDisplayName, String userHandle, String pfpUrl) {
        List<Webhook> webhooks = webhookRepository.findByIdServerAndStActiveTrue(serverId);

        for (Webhook webhook : webhooks) {
            if (webhook.getJsEvents() != null && webhook.getJsEvents().contains("MEMBER_JOIN")) {
                String template = webhook.getJsTemplates() != null ?
                        webhook.getJsTemplates().get("MEMBER_JOIN") : null;
                if (template != null) {
                    executeWebhook(webhook, template, Map.of(
                            "user", userDisplayName,
                            "user_handle", userHandle,
                            "user_id", userId.toString(),
                            "user_avatar", pfpUrl,
                            "server", getServerName(serverId)
                    ));
                }
            }
        }
    }

    public void executeMemberLeaveWebhook(UUID serverId, UUID userId,
                                          String userDisplayName, String userHandle, String pfpUrl) {
        List<Webhook> webhooks = webhookRepository.findByIdServerAndStActiveTrue(serverId);

        for (Webhook webhook : webhooks) {
            if (webhook.getJsEvents() != null && webhook.getJsEvents().contains("MEMBER_LEAVE")) {
                String template = webhook.getJsTemplates() != null ?
                        webhook.getJsTemplates().get("MEMBER_LEAVE") : null;
                if (template != null) {
                    executeWebhook(webhook, template, Map.of(
                            "user", userDisplayName,
                            "user_handle", userHandle,
                            "user_id", userId.toString(),
                            "user_avatar", pfpUrl,
                            "server", getServerName(serverId)
                    ));
                }
            }
        }
    }

    public void executeMemberKickWebhook(UUID serverId, UUID targetId, UUID moderatorId,
                                         String targetName, String moderatorName, String reason) {
        List<Webhook> webhooks = webhookRepository.findByIdServerAndStActiveTrue(serverId);

        for (Webhook webhook : webhooks) {
            if (webhook.getJsEvents() != null && webhook.getJsEvents().contains("MEMBER_KICK")) {
                String template = webhook.getJsTemplates() != null ?
                        webhook.getJsTemplates().get("MEMBER_KICK") : null;
                if (template != null) {
                    executeWebhook(webhook, template, Map.of(
                            "user", targetName,
                            "moderator", moderatorName,
                            "server", getServerName(serverId),
                            "reason", reason != null ? reason : ""
                    ));
                }
            }
        }
    }

    public void executeMemberBanWebhook(UUID serverId, UUID targetId, UUID moderatorId,
                                        String targetName, String moderatorName, String reason) {
        List<Webhook> webhooks = webhookRepository.findByIdServerAndStActiveTrue(serverId);

        for (Webhook webhook : webhooks) {
            if (webhook.getJsEvents() != null && webhook.getJsEvents().contains("MEMBER_BAN")) {
                String template = webhook.getJsTemplates() != null ?
                        webhook.getJsTemplates().get("MEMBER_BAN") : null;
                if (template != null) {
                    executeWebhook(webhook, template, Map.of(
                            "user", targetName,
                            "moderator", moderatorName,
                            "server", getServerName(serverId),
                            "reason", reason != null ? reason : ""
                    ));
                }
            }
        }
    }

    public void executeMemberTimeoutWebhook(UUID serverId, UUID targetId, UUID moderatorId,
                                            String targetName, String moderatorName,
                                            int durationMinutes, String reason) {
        List<Webhook> webhooks = webhookRepository.findByIdServerAndStActiveTrue(serverId);

        for (Webhook webhook : webhooks) {
            if (webhook.getJsEvents() != null && webhook.getJsEvents().contains("MEMBER_TIMEOUT")) {
                String template = webhook.getJsTemplates() != null ?
                        webhook.getJsTemplates().get("MEMBER_TIMEOUT") : null;
                if (template != null) {
                    executeWebhook(webhook, template, Map.of(
                            "user", targetName,
                            "moderator", moderatorName,
                            "duration", String.valueOf(durationMinutes),
                            "server", getServerName(serverId),
                            "reason", reason != null ? reason : ""
                    ));
                }
            }
        }
    }

    public void executeRoleAddWebhook(UUID serverId, UUID userId,
                                      String userDisplayName, String roleName) {
        List<Webhook> webhooks = webhookRepository.findByIdServerAndStActiveTrue(serverId);

        for (Webhook webhook : webhooks) {
            if (webhook.getJsEvents() != null && webhook.getJsEvents().contains("ROLE_ADD")) {
                String template = webhook.getJsTemplates() != null ?
                        webhook.getJsTemplates().get("ROLE_ADD") : null;
                if (template != null) {
                    executeWebhook(webhook, template, Map.of(
                            "user", userDisplayName,
                            "role", roleName,
                            "server", getServerName(serverId)
                    ));
                }
            }
        }
    }

    public void executeRoleRemoveWebhook(UUID serverId, UUID userId,
                                         String userDisplayName, String roleName) {
        List<Webhook> webhooks = webhookRepository.findByIdServerAndStActiveTrue(serverId);

        for (Webhook webhook : webhooks) {
            if (webhook.getJsEvents() != null && webhook.getJsEvents().contains("ROLE_REMOVE")) {
                String template = webhook.getJsTemplates() != null ?
                        webhook.getJsTemplates().get("ROLE_REMOVE") : null;
                if (template != null) {
                    executeWebhook(webhook, template, Map.of(
                            "user", userDisplayName,
                            "role", roleName,
                            "server", getServerName(serverId)
                    ));
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // CORE EXECUTION
    // ──────────────────────────────────────────────────────────────────────

    private void executeWebhook(Webhook webhook, String template, Map<String, String> variables) {
        try {
            String content = interpolateMessage(template, variables);

            Message saved = messageRepository.save(
                    Message.builder()
                            .idChannel(webhook.getIdChannel())
                            .idAuthor(null)
                            .dsContent(encryptionService.encrypt(content))
                            .tpMessage("webhook")
                            .jsActivity(Map.of(
                                    "webhookName", webhook.getDsName(),
                                    "webhookAvatar", webhook.getDsAvatarUrl(),
                                    "webhookBio", webhook.getDsBio(),
                                    "webhookBanner", webhook.getDsBannerUrl(),
                                    "webhookColor", webhook.getNrColor()
                            ))
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

    // ──────────────────────────────────────────────────────────────────────
    // CRUD OPERATIONS
    // ──────────────────────────────────────────────────────────────────────

    public List<Webhook> getServerWebhooks(UUID serverId, UUID requesterId) {
        if (!permissionService.can(requesterId, null, serverId, Permissions.MANAGE_SERVER)) {
            throw new ForbiddenException("You need MANAGE_SERVER permission to view webhooks.");
        }
        return webhookRepository.findByIdServerAndStActiveTrue(serverId);
    }

    @Transactional
    public Webhook createWebhook(UUID serverId, UUID channelId, UUID creatorId,
                                 String name, String avatarUrl, String bio,
                                 String bannerUrl, Integer color,
                                 List<String> events, Map<String, String> templates) {
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
                .jsEvents(events)
                .jsTemplates(templates)
                .stActive(true)
                .build();

        return webhookRepository.save(webhook);
    }

    @Transactional
    public Webhook updateWebhook(UUID webhookId, UUID userId, UpdateWebhookRequest req) {
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
        if (req.bio() != null) {
            webhook.setDsBio(req.bio());
        }
        if (req.bannerUrl() != null) {
            webhook.setDsBannerUrl(req.bannerUrl());
        }
        if (req.color() != null) {
            webhook.setNrColor(req.color());
        }
        if (req.events() != null) {
            webhook.setJsEvents(req.events());
        }
        if (req.templates() != null) {
            webhook.setJsTemplates(req.templates());
        }
        if (req.active() != null) {
            webhook.setStActive(req.active());
        }

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

    // ──────────────────────────────────────────────────────────────────────
    // REQUEST RECORDS
    // ──────────────────────────────────────────────────────────────────────

    public record UpdateWebhookRequest(
            String name,
            UUID channelId,
            String avatarUrl,
            String bio,
            String bannerUrl,
            Integer color,
            List<String> events,
            Map<String, String> templates,
            Boolean active
    ) {}
}