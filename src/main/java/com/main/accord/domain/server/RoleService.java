package com.main.accord.domain.server;

import com.main.accord.common.AccordException;
import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository       roleRepository;
    private final MemberRepository     memberRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final ServerRepository     serverRepository;
    private final PermissionService    permissionService;

    // ── List ─────────────────────────────────────────────────────────────────

    public List<Role> getRoles(UUID serverId, UUID requesterId) {
        assertMember(serverId, requesterId);
        return roleRepository.findByIdServerOrderByNrPositionDesc(serverId);
    }

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public Role createRole(UUID serverId, UUID requesterId, CreateRoleRequest req) {
        assertPermission(requesterId, serverId, Permissions.MANAGE_SERVER);

        if (roleRepository.existsByIdServerAndDsName(serverId, req.name())) {
            throw new AccordException("A role with that name already exists.");
        }

        // Position = current highest + 1
        List<Role> existing = roleRepository.findByIdServerOrderByNrPositionDesc(serverId);
        short nextPosition  = existing.isEmpty()
                ? 1
                : (short) (existing.get(0).getNrPosition() + 1);

        return roleRepository.save(
                Role.builder()
                        .idServer(serverId)
                        .dsName(req.name())
                        .nrColor(req.color())
                        .nrPermissions(req.permissions() != null ? req.permissions() : 0L)
                        .nrPosition(nextPosition)
                        .stMentionable(req.mentionable() != null && req.mentionable())
                        .stHoisted(req.hoisted() != null && req.hoisted())
                        .build()
        );
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public Role updateRole(UUID serverId, UUID roleId, UUID requesterId, UpdateRoleRequest req) {
        assertPermission(requesterId, serverId, Permissions.MANAGE_SERVER);

        Role role = roleRepository.findByIdRoleAndIdServer(roleId, serverId)
                .orElseThrow(() -> new NotFoundException("Role not found."));

        if (req.name()        != null) role.setDsName(req.name());
        if (req.color()       != null) role.setNrColor(req.color());
        if (req.permissions() != null) role.setNrPermissions(req.permissions());
        if (req.mentionable() != null) role.setStMentionable(req.mentionable());
        if (req.hoisted()     != null) role.setStHoisted(req.hoisted());

        return roleRepository.save(role);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteRole(UUID serverId, UUID roleId, UUID requesterId) {
        assertPermission(requesterId, serverId, Permissions.MANAGE_SERVER);

        roleRepository.findByIdRoleAndIdServer(roleId, serverId)
                .orElseThrow(() -> new NotFoundException("Role not found."));

        roleRepository.deleteByIdRoleAndIdServer(roleId, serverId);
    }

    // ── Assign / Revoke ───────────────────────────────────────────────────────

    @Transactional
    public void assignRole(UUID serverId, UUID targetUserId, UUID roleId, UUID requesterId) {
        assertPermission(requesterId, serverId, Permissions.MANAGE_SERVER);
        assertMember(serverId, targetUserId);

        roleRepository.findByIdRoleAndIdServer(roleId, serverId)
                .orElseThrow(() -> new NotFoundException("Role not found."));

        MemberRole.MemberRoleId pk = new MemberRole.MemberRoleId(serverId, targetUserId, roleId);
        if (!memberRoleRepository.existsById(pk)) {
            memberRoleRepository.save(
                    MemberRole.builder()
                            .idServer(serverId)
                            .idUser(targetUserId)
                            .idRole(roleId)
                            .build()
            );
        }
    }

    @Transactional
    public void revokeRole(UUID serverId, UUID targetUserId, UUID roleId, UUID requesterId) {
        assertPermission(requesterId, serverId, Permissions.MANAGE_SERVER);

        MemberRole.MemberRoleId pk = new MemberRole.MemberRoleId(serverId, targetUserId, roleId);
        memberRoleRepository.deleteById(pk);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void assertMember(UUID serverId, UUID userId) {
        if (!memberRepository.existsByIdServerAndIdUser(serverId, userId)) {
            throw new ForbiddenException("You are not a member of this server.");
        }
    }

    private void assertPermission(UUID userId, UUID serverId, long permission) {
        if (!permissionService.can(userId, null, serverId, permission)) {
            throw new ForbiddenException("You don't have permission to manage roles.");
        }
    }

    // ── Request records ───────────────────────────────────────────────────────

    public record CreateRoleRequest(
            String  name,
            Integer color,
            Long    permissions,
            Boolean mentionable,
            Boolean hoisted
    ) {}

    public record UpdateRoleRequest(
            String  name,
            Integer color,
            Long    permissions,
            Boolean mentionable,
            Boolean hoisted
    ) {}
}