package com.main.accord.domain.server;

import com.main.accord.common.AccordException;
import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.account.*;
import com.main.accord.domain.channel.ChReadStateRepository;
import com.main.accord.domain.notification.NotifType;
import com.main.accord.domain.notification.NotificationService;
import com.main.accord.domain.webhook.WebhookService;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import com.main.accord.websocket.ChatHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServerService {

    private final ServerRepository      serverRepository;
    private final MemberRepository      memberRepository;
    private final RoleRepository        roleRepository;
    private final InviteService         inviteService;
    private final BanService            banService;
    private final PermissionService     permissionService;
    private final ChatHandler           chatHandler;
    private final NotificationService   notificationService;
    private final ChReadStateRepository chReadStateRepository;
    private final AccountRepository     accountRepository;
    private final WebhookService        webhookService;
    private final VisualsRepository     visualsRepository;
    private final MemberRoleRepository  memberRoleRepository;

    // ── List ──────────────────────────────────────────────────────────────────

    public List<Server> getMyServers(UUID userId) {
        return serverRepository.findByMember(userId);
    }

    public Server getServer(UUID serverId, UUID requesterId) {
        if (!memberRepository.existsByIdServerAndIdUser(serverId, requesterId))
            throw new ForbiddenException("You are not a member of this server.");
        return serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found."));
    }

    public List<Member> getMembers(UUID serverId, UUID requesterId) {
        if (!memberRepository.existsByIdServerAndIdUser(serverId, requesterId))
            throw new ForbiddenException("You are not a member of this server.");
        return memberRepository.findByIdServer(serverId);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public Server createServer(UUID ownerId, String name) {
        Server server = serverRepository.save(
                Server.builder()
                        .idOwner(ownerId)
                        .dsName(name)
                        .nrPermissions(0L)
                        .build()
        );

        memberRepository.save(
                Member.builder()
                        .idServer(server.getIdServer())
                        .idUser(ownerId)
                        .build()
        );

        long everyonePermissions = Permissions.VIEW_CHANNELS |
                Permissions.SEND_MESSAGES |
                Permissions.READ_MESSAGE_HISTORY;

        Role everyoneRole = roleRepository.save(
                Role.builder()
                        .idServer(server.getIdServer())
                        .dsName("@everyone")
                        .nrPermissions(everyonePermissions)
                        .nrPosition((short) 0)
                        .build()
        );

        memberRoleRepository.save(
                MemberRole.builder()
                        .idServer(server.getIdServer())
                        .idUser(ownerId)
                        .idRole(everyoneRole.getIdRole())
                        .build()
        );

        Role adminRole = roleRepository.save(
                Role.builder()
                        .idServer(server.getIdServer())
                        .dsName("Admin")
                        .nrPermissions(Permissions.ADMINISTRATOR | Permissions.MANAGE_SERVER)
                        .nrPosition((short) 100)
                        .build()
        );

        memberRoleRepository.save(
                MemberRole.builder()
                        .idServer(server.getIdServer())
                        .idUser(ownerId)
                        .idRole(adminRole.getIdRole())
                        .build()
        );

        return server;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public Server updateServer(UUID serverId, UUID requesterId, UpdateServerRequest req) {
        if (!permissionService.can(requesterId, null, serverId, Permissions.MANAGE_SERVER))
            throw new ForbiddenException("You don't have permission to manage this server.");

        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found."));

        if (req.name()        != null) server.setDsName(req.name());
        if (req.description() != null) server.setDsDescription(req.description());
        if (req.iconUrl()     != null) server.setDsIconUrl(req.iconUrl());
        if (req.bannerUrl()   != null) server.setDsBannerUrl(req.bannerUrl());
        if (req.isPublic()    != null) server.setStPublic(req.isPublic());

        return serverRepository.save(server);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteServer(UUID serverId, UUID requesterId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found."));
        if (!server.getIdOwner().equals(requesterId))
            throw new ForbiddenException("Only the server owner can delete this server.");
        serverRepository.delete(server);
    }

    // ── Kick ──────────────────────────────────────────────────────────────────

    @Transactional
    public void kickMember(UUID requesterId, UUID serverId, UUID targetId, String reason) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found."));
        if (server.getIdOwner().equals(targetId))
            throw new ForbiddenException("Cannot kick the server owner.");
        if (!permissionService.can(requesterId, null, serverId, Permissions.KICK_MEMBERS))
            throw new ForbiddenException("You don't have permission to kick members.");

        assertRoleHierarchy(requesterId, targetId, serverId);

        Account targetAccount    = accountRepository.findById(targetId).orElse(null);
        Account moderatorAccount = accountRepository.findById(requesterId).orElse(null);
        String targetName    = targetAccount    != null ? targetAccount.getDsDisplayName()    : "User";
        String moderatorName = moderatorAccount != null ? moderatorAccount.getDsDisplayName() : "Moderator";

        memberRepository.deleteByIdServerAndIdUser(serverId, targetId);
        webhookService.executeMemberKickWebhook(serverId, targetId, requesterId, targetName, moderatorName, reason);

        chatHandler.sendToUser(targetId, Map.of(
                "type", "SERVER_KICK",
                "data", Map.of(
                        "serverId",    serverId,
                        "serverName",  server.getDsName(),
                        "reason",      reason != null ? reason : ""
                )
        ));

        notificationService.send(targetId, NotifType.ban,
                "You were kicked from " + server.getDsName(),
                reason != null ? reason : "No reason provided",
                Map.of("serverId", serverId.toString(), "serverName", server.getDsName(), "type", "server_kick")
        );
    }

    // ── Leave ─────────────────────────────────────────────────────────────────

    @Transactional
    public void leaveServer(UUID serverId, UUID userId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found."));
        if (server.getIdOwner().equals(userId))
            throw new AccordException("Transfer ownership before leaving.");
        if (!memberRepository.existsByIdServerAndIdUser(serverId, userId))
            throw new NotFoundException("You are not a member of this server.");

        Account account = accountRepository.findById(userId).orElse(null);
        Visuals visuals = visualsRepository.findById(userId).orElse(null);
        String userDisplayName = account != null ? account.getDsDisplayName() : "User";
        String userHandle      = account != null ? account.getDsHandle()      : "@handle";
        String pfpUrl = (visuals != null && visuals.getDsPfpUrl() != null)
                ? visuals.getDsPfpUrl()
                : "https://i.imgur.com/eTh2muI.png";

        memberRepository.deleteByIdServerAndIdUser(serverId, userId);
        webhookService.executeMemberLeaveWebhook(serverId, userId, userDisplayName, userHandle, pfpUrl);
    }

    // ── Timeout ───────────────────────────────────────────────────────────────

    @Transactional
    public Member timeoutMember(UUID requesterId, UUID serverId, UUID targetId,
                                int durationMinutes, String reason) {
        if (!permissionService.can(requesterId, null, serverId, Permissions.TIMEOUT_MEMBERS))
            throw new ForbiddenException("You don't have permission to timeout members.");

        assertRoleHierarchy(requesterId, targetId, serverId);

        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found."));
        if (server.getIdOwner().equals(targetId))
            throw new ForbiddenException("Cannot timeout the server owner.");

        Member member = memberRepository.findByIdServerAndIdUser(serverId, targetId)
                .orElseThrow(() -> new NotFoundException("Member not found."));

        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(durationMinutes);
        member.setStTimeout(true);
        member.setDtTimeoutExpires(expiresAt);
        Member saved = memberRepository.save(member);

        Account targetAccount    = accountRepository.findById(targetId).orElse(null);
        Account moderatorAccount = accountRepository.findById(requesterId).orElse(null);
        String targetName    = targetAccount    != null ? targetAccount.getDsDisplayName()    : "User";
        String moderatorName = moderatorAccount != null ? moderatorAccount.getDsDisplayName() : "Moderator";

        webhookService.executeMemberTimeoutWebhook(serverId, targetId, requesterId,
                targetName, moderatorName, durationMinutes, reason);

        chatHandler.sendToUser(targetId, Map.of(
                "type", "MEMBER_TIMEOUT",
                "data", Map.of(
                        "serverId",        serverId,
                        "serverName",      server.getDsName(),
                        "expiresAt",       expiresAt.toString(),
                        "durationMinutes", durationMinutes,
                        "reason",          reason != null ? reason : ""
                )
        ));

        notificationService.send(targetId, NotifType.timeout,
                "You have been timed out",
                "You were timed out in " + server.getDsName() + " for " + durationMinutes + " minutes.",
                Map.of("serverId", serverId.toString(), "serverName", server.getDsName(),
                        "durationMinutes", durationMinutes, "expiresAt", expiresAt.toString(),
                        "reason", reason != null ? reason : "", "type", "server_timeout")
        );

        return saved;
    }

    @Transactional
    public void removeTimeout(UUID requesterId, UUID serverId, UUID targetId) {
        if (!permissionService.can(requesterId, null, serverId, Permissions.TIMEOUT_MEMBERS))
            throw new ForbiddenException("You don't have permission to remove timeouts.");

        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found."));

        Member member = memberRepository.findByIdServerAndIdUser(serverId, targetId)
                .orElseThrow(() -> new NotFoundException("Member not found."));

        member.setStTimeout(false);
        member.setDtTimeoutExpires(null);
        memberRepository.save(member);

        chatHandler.sendToUser(targetId, Map.of(
                "type", "MEMBER_TIMEOUT_REMOVED",
                "data", Map.of("serverId", serverId, "serverName", server.getDsName())
        ));

        notificationService.send(targetId, NotifType.timeout,
                "Timeout removed",
                "Your timeout has been removed in " + server.getDsName(),
                Map.of("serverId", serverId.toString(), "serverName", server.getDsName(), "type", "timeout_removed")
        );
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void expireTimeouts() {
        List<Member> expiredMembers = memberRepository.findExpiredTimeouts(OffsetDateTime.now());
        for (Member member : expiredMembers) {
            UUID serverId = member.getIdServer();
            UUID userId   = member.getIdUser();
            Server server = serverRepository.findById(serverId).orElse(null);

            member.setStTimeout(false);
            member.setDtTimeoutExpires(null);
            memberRepository.save(member);

            if (server != null) {
                chatHandler.sendToUser(userId, Map.of(
                        "type", "MEMBER_TIMEOUT_EXPIRED",
                        "data", Map.of("serverId", serverId, "serverName", server.getDsName())
                ));
                notificationService.send(userId, NotifType.timeout,
                        "Timeout expired",
                        "Your timeout has expired in " + server.getDsName(),
                        Map.of("serverId", serverId.toString(), "serverName", server.getDsName(), "type", "timeout_expired")
                );
            }
        }
    }

    @Transactional
    public Member changeNickname(UUID serverId, UUID targetUserId, UUID requesterId, String nickname) {
        boolean isSelf    = targetUserId.equals(requesterId);
        boolean canManage = permissionService.can(requesterId, null, serverId, Permissions.MANAGE_NICKNAMES);

        if (!isSelf && !canManage)
            throw new ForbiddenException("You don't have permission to change other members' nicknames.");
        if (!isSelf)
            assertRoleHierarchy(requesterId, targetUserId, serverId);

        Member member = memberRepository.findByIdServerAndIdUser(serverId, targetUserId)
                .orElseThrow(() -> new NotFoundException("Member not found."));

        if (nickname != null && (nickname.isEmpty() || nickname.length() > 50))
            throw new AccordException("Nickname must be between 1 and 50 characters.");

        member.setDsNickname(nickname);
        Member saved = memberRepository.save(member);

        chatHandler.broadcastToChannel(serverId, Map.of(
                "type", "MEMBER_NICKNAME_CHANGE",
                "data", Map.of("serverId", serverId, "userId", targetUserId, "nickname", nickname)
        ));

        return saved;
    }

    // ── Ban / Unban ───────────────────────────────────────────────────────────

    @Transactional
    public ServerBan banMember(UUID requesterId, UUID serverId, UUID targetId, String reason) {
        return banService.banFromServer(requesterId, serverId, targetId, reason);
    }

    @Transactional
    public void unbanMember(UUID requesterId, UUID serverId, UUID targetId) {
        banService.unbanFromServer(requesterId, serverId, targetId);
    }

    @Transactional
    public Member updateMember(UUID requesterId, UUID serverId, UUID targetId, UpdateMemberRequest req) {
        Member member = memberRepository.findByIdServerAndIdUser(serverId, targetId)
                .orElseThrow(() -> new NotFoundException("Member not found."));

        if (req.muted() != null) {
            if (!permissionService.can(requesterId, null, serverId, Permissions.MUTE_MEMBERS))
                throw new ForbiddenException("You don't have permission to mute members.");
            member.setStMuted(req.muted());
        }

        if (req.deafened() != null) {
            if (!permissionService.can(requesterId, null, serverId, Permissions.DEAFEN_MEMBERS))
                throw new ForbiddenException("You don't have permission to deafen members.");
            member.setStDeafened(req.deafened());
        }

        Member saved = memberRepository.save(member);

        chatHandler.sendToUser(targetId, Map.of(
                "type", "MEMBER_UPDATE",
                "data", Map.of("serverId", serverId, "muted", saved.getStMuted(), "deafened", saved.getStDeafened())
        ));

        return saved;
    }

    // ── Join by invite ────────────────────────────────────────────────────────

    @Transactional
    public Invite joinByInvite(UUID userId, String code) {
        return inviteService.joinByCode(userId, code);
    }

    /**
     * Uses the typed ChReadStateRepository.ServerUnreadCount projection.
     * Previously cast Object[] by position — now fully type-safe.
     */
    public List<ServerSummaryDto> getServerSummaries(UUID userId) {
        List<Server> servers = serverRepository.findByMember(userId);
        if (servers.isEmpty()) return List.of();

        List<UUID> serverIds = servers.stream().map(Server::getIdServer).toList();

        Map<UUID, Long> unreadMap = chReadStateRepository
                .countUnreadPerServer(userId, serverIds)
                .stream()
                .collect(Collectors.toMap(
                        row -> UUID.fromString(row.getServerId()),
                        ChReadStateRepository.ServerUnreadCount::getUnreadCount
                ));

        return servers.stream().map(s -> ServerSummaryDto.builder()
                .idServer(s.getIdServer())
                .dsName(s.getDsName())
                .dsIconUrl(s.getDsIconUrl())
                .dsDescription(s.getDsDescription())
                .nrUnread(unreadMap.getOrDefault(s.getIdServer(), 0L))
                .build()
        ).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertRoleHierarchy(UUID requesterId, UUID targetId, UUID serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found."));
        if (server.getIdOwner().equals(requesterId)) return;

        short requesterTop = memberRepository.getHighestRolePosition(requesterId, serverId);
        short targetTop    = memberRepository.getHighestRolePosition(targetId, serverId);

        if (requesterTop >= targetTop)
            throw new ForbiddenException("You cannot action someone with an equal or higher role.");
    }

    public long getMyPermissions(UUID userId, UUID serverId) {
        if (!memberRepository.existsByIdServerAndIdUser(serverId, userId))
            throw new ForbiddenException("You are not a member of this server.");
        return permissionService.computeEffective(userId, null, serverId);
    }

    public boolean isMember(UUID serverId, UUID userId) {
        return memberRepository.existsByIdServerAndIdUser(serverId, userId);
    }

    public Member getMember(UUID serverId, UUID userId) {
        return memberRepository.findByIdServerAndIdUser(serverId, userId)
                .orElseThrow(() -> new NotFoundException("Member not found."));
    }

    public record UpdateMemberRequest(Boolean muted, Boolean deafened) {}

    public record UpdateServerRequest(
            String  name,
            String  description,
            String  iconUrl,
            String  bannerUrl,
            Boolean isPublic
    ) {}
}