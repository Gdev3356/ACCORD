package com.main.accord.domain.server;

import com.main.accord.common.AccordException;
import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
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

@Service
@RequiredArgsConstructor
public class ServerService {

    private final ServerRepository  serverRepository;
    private final MemberRepository  memberRepository;
    private final RoleRepository    roleRepository;
    private final InviteService     inviteService;
    private final BanService        banService;
    private final PermissionService permissionService;
    private final ChatHandler       chatHandler;

    // ── List ──────────────────────────────────────────────────────────────────

    public List<Server> getMyServers(UUID userId) {
        return serverRepository.findByMember(userId);
    }

    public Server getServer(UUID serverId, UUID requesterId) {
        if (!memberRepository.existsByIdServerAndIdUser(serverId, requesterId)) {
            throw new ForbiddenException("You are not a member of this server.");
        }
        return serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found."));
    }

    public List<Member> getMembers(UUID serverId, UUID requesterId) {
        if (!memberRepository.existsByIdServerAndIdUser(serverId, requesterId)) {
            throw new ForbiddenException("You are not a member of this server.");
        }
        return memberRepository.findByIdServer(serverId);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public Server createServer(UUID ownerId, String name) {
        // Calculate ALL permissions (or at least the important ones)
        long allPermissions =
                Permissions.VIEW_CHANNELS |
                        Permissions.SEND_MESSAGES |
                        Permissions.READ_MESSAGE_HISTORY |
                        Permissions.ATTACH_FILES |
                        Permissions.EMBED_LINKS |
                        Permissions.KICK_MEMBERS |      // 16384
                        Permissions.BAN_MEMBERS |       // 32768
                        Permissions.MANAGE_SERVER |     // 131072
                        Permissions.MANAGE_CHANNELS |   // 65536
                        Permissions.MUTE_MEMBERS |      // 2048
                        Permissions.DEAFEN_MEMBERS |    // 4096
                        Permissions.MOVE_MEMBERS |      // 8192
                        Permissions.MANAGE_MESSAGES |   // 8
                        Permissions.MENTION_EVERYONE |  // 128
                        Permissions.SEND_TTS;           // 4

        Server server = serverRepository.save(
                Server.builder()
                        .idOwner(ownerId)
                        .dsName(name)
                        .nrPermissions(allPermissions)  // ← Use all permissions
                        .build()
        );

        memberRepository.save(
                Member.builder()
                        .idServer(server.getIdServer())
                        .idUser(ownerId)
                        .build()
        );

        return server;
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public Server updateServer(UUID serverId, UUID requesterId, UpdateServerRequest req) {
        if (!permissionService.can(requesterId, null, serverId, Permissions.MANAGE_SERVER)) {
            throw new ForbiddenException("You don't have permission to manage this server.");
        }
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
        if (!server.getIdOwner().equals(requesterId)) {
            throw new ForbiddenException("Only the server owner can delete this server.");
        }
        serverRepository.delete(server);
    }

    // ── Kick ──────────────────────────────────────────────────────────────────

    @Transactional
    public void kickMember(UUID requesterId, UUID serverId, UUID targetId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found."));
        if (server.getIdOwner().equals(targetId)) {
            throw new ForbiddenException("Cannot kick the server owner.");
        }
        if (!permissionService.can(requesterId, null, serverId, Permissions.KICK_MEMBERS)) {
            throw new ForbiddenException("You don't have permission to kick members.");
        }
        assertRoleHierarchy(requesterId, targetId, serverId);
        memberRepository.deleteByIdServerAndIdUser(serverId, targetId);
        chatHandler.sendToUser(targetId, Map.of(
                "type", "SERVER_KICK",
                "data", Map.of("serverId", serverId)
        ));
    }

    // ── Leave ─────────────────────────────────────────────────────────────────

    @Transactional
    public void leaveServer(UUID serverId, UUID userId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found."));
        if (server.getIdOwner().equals(userId)) {
            throw new AccordException("Transfer ownership before leaving.");
        }
        if (!memberRepository.existsByIdServerAndIdUser(serverId, userId)) {
            throw new NotFoundException("You are not a member of this server.");
        }
        memberRepository.deleteByIdServerAndIdUser(serverId, userId);
    }

    // ── Muting Member ──────────────────────────────────────────────────────────────
    @Transactional
    public Member timeoutMember(UUID requesterId, UUID serverId, UUID targetId,
                                int durationMinutes, String reason) {
        // Check permission
        if (!permissionService.can(requesterId, null, serverId, Permissions.TIMEOUT_MEMBERS)) {
            throw new ForbiddenException("You don't have permission to timeout members.");
        }

        // Check role hierarchy (can't timeout someone with higher/equal role)
        assertRoleHierarchy(requesterId, targetId, serverId);

        // Can't timeout the owner
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found."));
        if (server.getIdOwner().equals(targetId)) {
            throw new ForbiddenException("Cannot timeout the server owner.");
        }

        Member member = memberRepository.findByIdServerAndIdUser(serverId, targetId)
                .orElseThrow(() -> new NotFoundException("Member not found."));

        OffsetDateTime expiresAt = OffsetDateTime.now().plusMinutes(durationMinutes);
        member.setStTimeout(true);
        member.setDtTimeoutExpires(expiresAt);

        Member saved = memberRepository.save(member);

        // Notify via WebSocket
        chatHandler.sendToUser(targetId, Map.of(
                "type", "MEMBER_TIMEOUT",
                "data", Map.of(
                        "serverId", serverId,
                        "expiresAt", expiresAt.toString(),
                        "durationMinutes", durationMinutes,
                        "reason", reason != null ? reason : ""
                )
        ));

        return saved;
    }

    @Transactional
    public void removeTimeout(UUID requesterId, UUID serverId, UUID targetId) {
        if (!permissionService.can(requesterId, null, serverId, Permissions.TIMEOUT_MEMBERS)) {
            throw new ForbiddenException("You don't have permission to remove timeouts.");
        }

        Member member = memberRepository.findByIdServerAndIdUser(serverId, targetId)
                .orElseThrow(() -> new NotFoundException("Member not found."));

        member.setStTimeout(false);
        member.setDtTimeoutExpires(null);
        memberRepository.save(member);

        chatHandler.sendToUser(targetId, Map.of(
                "type", "MEMBER_TIMEOUT_REMOVED",
                "data", Map.of("serverId", serverId)
        ));
    }

    @Scheduled(fixedDelay = 60000) // Run every minute
    @Transactional
    public void expireTimeouts() {
        int expired = memberRepository.expireTimeouts(OffsetDateTime.now());
    }

    @Transactional
    public Member changeNickname(UUID serverId, UUID targetUserId, UUID requesterId, String nickname) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found."));

        // Check permission - users can change their own nickname, or mods can change others
        boolean isSelf = targetUserId.equals(requesterId);
        boolean canManage = permissionService.can(requesterId, null, serverId, Permissions.MANAGE_NICKNAMES);

        if (!isSelf && !canManage) {
            throw new ForbiddenException("You don't have permission to change other members' nicknames.");
        }

        // Check role hierarchy if changing someone else's nickname
        if (!isSelf) {
            assertRoleHierarchy(requesterId, targetUserId, serverId);
        }

        Member member = memberRepository.findByIdServerAndIdUser(serverId, targetUserId)
                .orElseThrow(() -> new NotFoundException("Member not found."));

        // Validate nickname length
        if (nickname != null && (nickname.length() < 1 || nickname.length() > 50)) {
            throw new AccordException("Nickname must be between 1 and 50 characters.");
        }

        member.setDsNickname(nickname);
        Member saved = memberRepository.save(member);

        // Broadcast nickname change via WebSocket
        chatHandler.broadcastToChannel(serverId, Map.of(
                "type", "MEMBER_NICKNAME_CHANGE",
                "data", Map.of(
                        "serverId", serverId,
                        "userId", targetUserId,
                        "nickname", nickname
                )
        ));

        return saved;
    }

    // ── Ban / Unban — delegate to BanService ──────────────────────────────────

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
            if (!permissionService.can(requesterId, null, serverId, Permissions.MUTE_MEMBERS)) {
                throw new ForbiddenException("You don't have permission to mute members.");
            }
            member.setStMuted(req.muted());
        }

        if (req.deafened() != null) {
            if (!permissionService.can(requesterId, null, serverId, Permissions.DEAFEN_MEMBERS)) {
                throw new ForbiddenException("You don't have permission to deafen members.");
            }
            member.setStDeafened(req.deafened());
        }

        Member saved = memberRepository.save(member);

        chatHandler.sendToUser(targetId, Map.of(
                "type", "MEMBER_UPDATE",
                "data", Map.of(
                        "serverId",  serverId,
                        "muted",     saved.getStMuted(),
                        "deafened",  saved.getStDeafened()
                )
        ));

        return saved;
    }

    public record UpdateMemberRequest(Boolean muted, Boolean deafened) {}

    // ── Join by invite — delegate to InviteService ────────────────────────────

    @Transactional
    public Invite joinByInvite(UUID userId, String code) {
        return inviteService.joinByCode(userId, code);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertRoleHierarchy(UUID requesterId, UUID targetId, UUID serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found."));

        // Server owner can kick anyone
        if (server.getIdOwner().equals(requesterId)) {
            return;
        }

        short requesterTop = memberRepository.getHighestRolePosition(requesterId, serverId);
        short targetTop    = memberRepository.getHighestRolePosition(targetId,    serverId);

        // Requester needs STRICTLY HIGHER priority (LOWER number)
        if (requesterTop >= targetTop) {
            throw new ForbiddenException("You cannot action someone with an equal or higher role.");
        }
    }

    public long getMyPermissions(UUID userId, UUID serverId) {
        // Throws ForbiddenException if not a member, which is the right behavior
        if (!memberRepository.existsByIdServerAndIdUser(serverId, userId)) {
            throw new ForbiddenException("You are not a member of this server.");
        }
        return permissionService.computeEffective(userId, null, serverId);
    }

    // ── Request records ───────────────────────────────────────────────────────

    public record UpdateServerRequest(
            String  name,
            String  description,
            String  iconUrl,
            String  bannerUrl,
            Boolean isPublic
    ) {}
}