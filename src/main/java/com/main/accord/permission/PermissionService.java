package com.main.accord.permission;

import com.main.accord.domain.channel.PermissionOverride;
import com.main.accord.domain.channel.PermissionOverrideRepository;
import com.main.accord.domain.server.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final MemberRepository           memberRepository;
    private final MemberRoleRepository       memberRoleRepository;
    private final PermissionOverrideRepository overrideRepository;

    /**
     * Returns the effective permission bitmask for a user in a specific channel.
     */
    public long computeEffective(UUID userId, UUID channelId, UUID serverId) {
        // 1. Server owner has every permission
        Server server = memberRepository.findServer(serverId);
        if (server.getIdOwner().equals(userId)) {
            return ~0L;
        }

        // 2. Collect all role permission masks for this member
        List<Role> memberRoles = memberRoleRepository.findRolesByMember(userId, serverId);

        // 3. Check ADMINISTRATOR shortcut
        boolean isAdmin = memberRoles.stream()
                .anyMatch(r -> has(r.getNrPermissions(), Permissions.ADMINISTRATOR));
        if (isAdmin) return ~0L;

        // 4. Base: server default permissions OR'd with all role permissions
        long base = server.getNrPermissions();
        for (Role role : memberRoles) {
            base |= role.getNrPermissions();
        }

        // 5. Apply per-role channel overrides
        List<UUID> roleIds = memberRoles.stream().map(Role::getIdRole).toList();
        List<PermissionOverride> roleOverrides =
                overrideRepository.findByChannelAndRoles(channelId, roleIds);

        for (PermissionOverride ov : roleOverrides) {
            base &= ~ov.getNrDeny();
            base |=  ov.getNrAllow();
        }

        // 6. Apply member-specific override (highest priority)
        var memberOverride = overrideRepository.findByChannelAndUser(channelId, userId);
        if (memberOverride.isPresent()) {
            PermissionOverride ov = memberOverride.get();
            base &= ~ov.getNrDeny();
            base |=  ov.getNrAllow();
        }

        return base;
    }

    /** Convenience check: does this user have a specific permission in a channel? */
    public boolean can(UUID userId, UUID channelId, UUID serverId, long permission) {
        long effective = computeEffective(userId, channelId, serverId);
        return has(effective, permission);
    }

    public static boolean has(long mask, long permission) {
        return (mask & permission) == permission;
    }
}