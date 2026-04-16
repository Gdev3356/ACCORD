package com.main.accord.domain.server;

import com.main.accord.common.AccordException;
import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.account.Account;
import com.main.accord.domain.account.AccountRepository;
import com.main.accord.domain.account.Visuals;
import com.main.accord.domain.account.VisualsRepository;
import com.main.accord.domain.channel.ChannelRepository;
import com.main.accord.domain.channel.ChannelType;
import com.main.accord.domain.notification.NotifType;
import com.main.accord.domain.notification.NotificationService;
import com.main.accord.domain.webhook.WebhookService;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import com.main.accord.websocket.ChatHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InviteService {

    private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int    CODE_LENGTH   = 8;

    private final InviteRepository  inviteRepository;
    private final MemberRepository  memberRepository;
    private final ServerRepository  serverRepository;
    private final PermissionService permissionService;
    private final NotificationService notificationService;
    private final ChatHandler chatHandler;
    private final ChannelRepository channelRepository;
    private final AccountRepository accountRepository;
    private final VisualsRepository visualsRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final RoleRepository roleRepository;
    private final WebhookService webhookService;

    // ── List invites for a server ─────────────────────────────────────────────

    public List<Invite> getInvites(UUID serverId, UUID requesterId) {
        assertMember(serverId, requesterId);
        return inviteRepository.findActiveByServer(serverId);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public Invite createInvite(UUID serverId, UUID requesterId, CreateInviteRequest req) {
        assertMember(serverId, requesterId);

        OffsetDateTime expires = null;
        if (req.expiresInSeconds() != null && req.expiresInSeconds() > 0) {
            expires = OffsetDateTime.now().plusSeconds(req.expiresInSeconds());
        }

        return inviteRepository.save(
                Invite.builder()
                        .dsCode(generateCode())
                        .idServer(serverId)
                        .idChannel(req.channelId())
                        .idCreator(requesterId)
                        .nrMaxUses(req.maxUses())
                        .dtExpires(expires)
                        .build()
        );
    }

    // ── Revoke ────────────────────────────────────────────────────────────────

    @Transactional
    public void revokeInvite(UUID serverId, UUID inviteId, UUID requesterId) {
        if (!permissionService.can(requesterId, null, serverId, Permissions.MANAGE_SERVER)) {
            throw new ForbiddenException("You don't have permission to revoke invites.");
        }

        Invite invite = inviteRepository.findById(inviteId)
                .orElseThrow(() -> new NotFoundException("Invite not found."));

        if (!invite.getIdServer().equals(serverId)) {
            throw new NotFoundException("Invite not found.");
        }

        invite.setStStatus(InviteStatus.revoked);
        inviteRepository.save(invite);
    }

    private List<Map<String, Object>> getMemberRoles(UUID serverId, UUID userId) {
        List<MemberRole> memberRoles = memberRoleRepository.findByIdServerAndIdUser(serverId, userId);

        return memberRoles.stream()
                .map(mr -> roleRepository.findByIdRoleAndIdServer(mr.getIdRole(), serverId).orElse(null))
                .filter(role -> role != null)
                .<Map<String, Object>>map(role -> {
                    Map<String, Object> roleMap = new java.util.HashMap<>();
                    roleMap.put("idRole", role.getIdRole().toString());
                    roleMap.put("dsName", role.getDsName());
                    roleMap.put("nrColor", role.getNrColor() != null ? role.getNrColor() : 0x818cf8);
                    return roleMap;
                })
                .collect(Collectors.toList());
    }

    // ── Join by code ─────────────────────────────────────────────────────────

    @Transactional
    public Invite joinByCode(UUID userId, String code) {
        OffsetDateTime now = OffsetDateTime.now();
        inviteRepository.expireStale(now);

        Invite invite = inviteRepository.findByDsCode(code)
                .orElseThrow(() -> new NotFoundException("Invalid invite code."));

        if (invite.getStStatus() != InviteStatus.active) {
            throw new AccordException("This invite has expired or been revoked.");
        }

        if (invite.getDtExpires() != null && invite.getDtExpires().isBefore(OffsetDateTime.now())) {
            invite.setStStatus(InviteStatus.expired);
            inviteRepository.save(invite);
            throw new AccordException("This invite has expired.");
        }

        UUID serverId = invite.getIdServer();

        if (memberRepository.existsByIdServerAndIdUser(serverId, userId)) {
            return invite;
        }

        serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server no longer exists."));

        // Save the member
        memberRepository.save(
                Member.builder()
                        .idServer(serverId)
                        .idUser(userId)
                        .build()
        );

        invite.setNrUses(invite.getNrUses() + 1);
        if (invite.getNrMaxUses() != null && invite.getNrUses() >= invite.getNrMaxUses()) {
            invite.setStStatus(InviteStatus.expired);
        }
        inviteRepository.save(invite);

        // Notify the invite creator
        if (invite.getIdCreator() != null && !invite.getIdCreator().equals(userId)) {
            notificationService.send(
                    invite.getIdCreator(),
                    NotifType.server_invite,
                    "Someone joined your server",
                    "A user joined via your invite link.",
                    Map.of(
                            "serverId", serverId.toString(),
                            "userId",   userId.toString(),
                            "code",     code
                    )
            );
        }

        // Fetch user data
        Account account = accountRepository.findById(userId).orElse(null);
        Visuals visuals = visualsRepository.findById(userId).orElse(null);

        String displayName = account != null ? account.getDsDisplayName() : "User";
        String handle = account != null ? account.getDsHandle() : "";
        String pfpUrl = (visuals != null && visuals.getDsPfpUrl() != null)
                ? visuals.getDsPfpUrl()
                : "https://i.imgur.com/eTh2muI.png";

        // Get member roles as formatted maps
        List<Map<String, Object>> memberRoles = getMemberRoles(serverId, userId);

        // Prepare member data
        Map<String, Object> memberData = new java.util.HashMap<>();
        memberData.put("userId", userId.toString());
        memberData.put("displayName", displayName);
        memberData.put("handle", handle);
        memberData.put("pfpUrl", pfpUrl);
        memberData.put("nickname", null);
        memberData.put("roles", memberRoles);

        // Trigger webhook
        webhookService.executeMemberJoinWebhook(serverId, userId, displayName, handle, pfpUrl);

        // Broadcast to all server members
        List<Member> allMembers = memberRepository.findByIdServer(serverId);
        for (Member member : allMembers) {
            if (!member.getIdUser().equals(userId)) {
                chatHandler.sendToUser(member.getIdUser(), Map.of(
                        "type", "MEMBER_JOIN",
                        "data", memberData
                ));
            }
        }

        // Broadcast to first text channel
        channelRepository.findByIdServerOrderByNrPositionAsc(serverId).stream()
                .filter(c -> c.getTpChannel() == ChannelType.text)
                .findFirst()
                .ifPresent(firstTextChannel ->
                        chatHandler.broadcastToChannel(firstTextChannel.getIdChannel(), Map.of(
                                "type", "MEMBER_JOIN",
                                "data", memberData
                        ))
                );

        // Send to joining user
        chatHandler.sendToUser(userId, Map.of(
                "type", "MEMBER_JOIN_SELF",
                "data", Map.of(
                        "userId", userId.toString(),
                        "displayName", displayName,
                        "handle", handle,
                        "pfpUrl", pfpUrl,
                        "serverId", serverId.toString()
                )
        ));

        return invite;
    }

    // ── Lookup ───────────────────────────────────────────────────────────────

    public Invite previewInvite(String code) {
        OffsetDateTime now = OffsetDateTime.now();
        inviteRepository.expireStale(now);
        Invite invite = inviteRepository.findByDsCode(code)
                .orElseThrow(() -> new NotFoundException("Invalid invite code."));

        if (invite.getStStatus() != InviteStatus.active) {
            throw new AccordException("This invite has expired or been revoked.");
        }
        return invite;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertMember(UUID serverId, UUID userId) {
        if (!memberRepository.existsByIdServerAndIdUser(serverId, userId)) {
            throw new ForbiddenException("You are not a member of this server.");
        }
    }

    private String generateCode() {
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET.charAt(rng.nextInt(CODE_ALPHABET.length())));
        }
        String code = sb.toString();
        return inviteRepository.findByDsCode(code).isPresent() ? generateCode() : code;
    }

    // ── Request records ───────────────────────────────────────────────────────

    public record CreateInviteRequest(
            UUID    channelId,
            Integer maxUses,
            Long    expiresInSeconds
    ) {}
}