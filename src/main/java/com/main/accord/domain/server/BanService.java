package com.main.accord.domain.server;

import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.account.Account;
import com.main.accord.domain.account.AccountRepository;
import com.main.accord.domain.account.BanLog;
import com.main.accord.domain.account.BanLogRepository;
import com.main.accord.domain.notification.NotifType;
import com.main.accord.domain.notification.NotificationService;
import com.main.accord.domain.webhook.WebhookService;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import com.main.accord.websocket.ChatHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BanService {


    private final ServerBanRepository serverBanRepository;
    private final BanLogRepository    banLogRepository;
    private final MemberRepository    memberRepository;
    private final ServerRepository    serverRepository;
    private final PermissionService   permissionService;
    private final ChatHandler         chatHandler;
    private final NotificationService notificationService;
    private final AccountRepository   accountRepository;
    private final WebhookService      webhookService;

    // ── Server ban ────────────────────────────────────────────────────────────

    @Transactional
    public ServerBan banFromServer(UUID requesterId, UUID serverId, UUID targetId, String reason) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found."));

        if (server.getIdOwner().equals(targetId)) {
            throw new ForbiddenException("Cannot ban the server owner.");
        }

        if (!permissionService.can(requesterId, null, serverId, Permissions.BAN_MEMBERS)) {
            throw new ForbiddenException("You don't have permission to ban members.");
        }

        assertRoleHierarchy(requesterId, targetId, serverId);

        ServerBan ban = serverBanRepository.save(
                ServerBan.builder()
                        .idServer(serverId)
                        .idUser(targetId)
                        .idBannedBy(requesterId)
                        .dsReason(reason)
                        .build()
        );

        Account targetAccount = accountRepository.findById(targetId).orElse(null);
        Account moderatorAccount = accountRepository.findById(requesterId).orElse(null);
        String targetName = targetAccount != null ? targetAccount.getDsDisplayName() : "User";
        String moderatorName = moderatorAccount != null ? moderatorAccount.getDsDisplayName() : "Moderator";

        if (memberRepository.existsByIdServerAndIdUser(serverId, targetId)) {
            memberRepository.deleteByIdServerAndIdUser(serverId, targetId);
        }

        webhookService.executeMemberBanWebhook(serverId, targetId, requesterId, targetName, moderatorName, reason);

        // Persist a notification record AND push it over WebSocket before their session drops
        notificationService.send(
                targetId,
                NotifType.ban,
                "You have been banned",
                reason != null ? reason : "You were banned from a server.",
                Map.of(
                        "serverId", serverId.toString(),
                        "type",     "server_ban"
                )
        );

        // Separate WebSocket push so the client knows to immediately close that server's UI
        chatHandler.sendToUser(targetId, Map.of(
                "type", "SERVER_BAN",
                "data", Map.of(
                        "serverId", serverId,
                        "reason",   reason != null ? reason : ""
                )
        ));

        return ban;
    }

    @Transactional
    public void unbanFromServer(UUID requesterId, UUID serverId, UUID targetId) {
        if (!permissionService.can(requesterId, null, serverId, Permissions.BAN_MEMBERS)) {
            throw new ForbiddenException("You don't have permission to unban members.");
        }

        ServerBan ban = serverBanRepository.findByIdServerAndIdUser(serverId, targetId)
                .orElseThrow(() -> new NotFoundException("No active ban found for this user."));

        serverBanRepository.delete(ban);
    }

    public List<ServerBan> getServerBans(UUID requesterId, UUID serverId) {
        if (!permissionService.can(requesterId, null, serverId, Permissions.BAN_MEMBERS)) {
            throw new ForbiddenException("You don't have permission to view bans.");
        }
        return serverBanRepository.findByIdServer(serverId);
    }

    // ── Platform ban (admin/service role only) ─────────────────────────────────
    // These are called from your admin tooling, not a public endpoint.
    // The controller for this should be protected — only accessible with the
    // service role key or an internal admin flag, never from the regular client.

    @Transactional
    public BanLog platformBan(UUID adminId, UUID targetId, String reason, OffsetDateTime expires) {
        BanLog ban = banLogRepository.save(
                BanLog.builder()
                        .idUser(targetId)
                        .idBannedBy(adminId)
                        .dsReason(reason)
                        .dtExpires(expires)
                        .stLifted(false)
                        .build()
        );

        notificationService.send(
                targetId,
                NotifType.ban,
                "Your account has been suspended",
                reason != null ? reason : "Your account has been suspended.",
                Map.of(
                        "type",    "platform_ban",
                        "expires", expires != null ? expires.toString() : "permanent"
                )
        );

        chatHandler.sendToUser(targetId, Map.of(
                "type", "PLATFORM_BAN",
                "data", Map.of(
                        "reason",  reason != null ? reason : "",
                        "expires", expires != null ? expires.toString() : "permanent"
                )
        ));

        return ban;
    }

    @Transactional
    public void liftPlatformBan(UUID adminId, UUID banId) {
        BanLog ban = banLogRepository.findById(banId)
                .orElseThrow(() -> new NotFoundException("Ban not found."));

        ban.setStLifted(true);
        ban.setDtLiftedAt(OffsetDateTime.now());
        ban.setIdLiftedBy(adminId);
        banLogRepository.save(ban);
    }

    // ── Role hierarchy check ──────────────────────────────────────────────────
    // Prevents lower-ranked mods from banning higher-ranked members.
    // Highest NR_POSITION = most powerful.

    private void assertRoleHierarchy(UUID requesterId, UUID targetId, UUID serverId) {
        short requesterTop = memberRepository.getHighestRolePosition(requesterId, serverId);
        short targetTop    = memberRepository.getHighestRolePosition(targetId,    serverId);

        if (requesterTop <= targetTop) {
            throw new ForbiddenException("You cannot ban someone with an equal or higher role.");
        }
    }

    // ── Ban guard (called by JwtAuthFilter on every request) ──────────────────

    public boolean isServerBanned(UUID userId, UUID serverId) {
        return serverBanRepository.existsByIdServerAndIdUser(serverId, userId);
    }

    public boolean isPlatformBanned(UUID userId) {
        return banLogRepository.isCurrentlyBanned(userId);
    }
}