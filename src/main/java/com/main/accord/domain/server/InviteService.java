package com.main.accord.domain.server;

import com.main.accord.common.AccordException;
import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InviteService {

    private static final String CODE_ALPHABET = "ABCDEFGHIJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int    CODE_LENGTH   = 8;

    private final InviteRepository  inviteRepository;
    private final MemberRepository  memberRepository;
    private final ServerRepository  serverRepository;
    private final PermissionService permissionService;

    // ── List invites for a server ─────────────────────────────────────────────

    public List<Invite> getInvites(UUID serverId, UUID requesterId) {
        assertMember(serverId, requesterId);
        return inviteRepository.findActiveByServer(serverId);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public Invite createInvite(UUID serverId, UUID requesterId, CreateInviteRequest req) {
        // Any member can create an invite by default — servers can restrict this
        // via the MANAGE_SERVER permission if they want; keeping it open for now
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
                        .nrMaxUses(req.maxUses())   // null = unlimited
                        .dtExpires(expires)          // null = never
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

    // ── Join by code (called from ServerService / public endpoint) ─────────────

    @Transactional
    public Invite joinByCode(UUID userId, String code) {
        // Clean up any stale invites first
        inviteRepository.expireStale();

        Invite invite = inviteRepository.findByDsCode(code)
                .orElseThrow(() -> new NotFoundException("Invalid invite code."));

        if (invite.getStStatus() != InviteStatus.active) {
            throw new AccordException("This invite has expired or been revoked.");
        }

        // Check expiry explicitly (expireStale covers bulk but not this exact moment)
        if (invite.getDtExpires() != null && invite.getDtExpires().isBefore(OffsetDateTime.now())) {
            invite.setStStatus(InviteStatus.expired);
            inviteRepository.save(invite);
            throw new AccordException("This invite has expired.");
        }

        UUID serverId = invite.getIdServer();

        // Already a member — silently succeed
        if (memberRepository.existsByIdServerAndIdUser(serverId, userId)) {
            return invite;
        }

        // Check server exists
        serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server no longer exists."));

        // Add member
        memberRepository.save(
                Member.builder()
                        .idServer(serverId)
                        .idUser(userId)
                        .build()
        );

        // Increment use count and auto-expire if max uses reached
        invite.setNrUses(invite.getNrUses() + 1);
        if (invite.getNrMaxUses() != null && invite.getNrUses() >= invite.getNrMaxUses()) {
            invite.setStStatus(InviteStatus.expired);
        }
        inviteRepository.save(invite);

        return invite;
    }

    // ── Lookup (for preview before joining) ───────────────────────────────────

    public Invite previewInvite(String code) {
        inviteRepository.expireStale();
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
        // Collision is astronomically unlikely but guard anyway
        String code = sb.toString();
        return inviteRepository.findByDsCode(code).isPresent() ? generateCode() : code;
    }

    // ── Request records ───────────────────────────────────────────────────────

    public record CreateInviteRequest(
            UUID    channelId,      // optional — which channel the invite links to
            Integer maxUses,        // null = unlimited
            Long    expiresInSeconds // null = never; e.g. 86400 = 24 hours
    ) {}
}