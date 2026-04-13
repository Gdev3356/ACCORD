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

    private final MemberRepository             memberRepository;
    private final MemberRoleRepository         memberRoleRepository;
    private final PermissionOverrideRepository overrideRepository;

    public long computeEffective(UUID userId, UUID channelId, UUID serverId) {
        // 1. Server owner — all permissions
        Server server = memberRepository.findServer(serverId);
        if (server.getIdOwner().equals(userId)) return ~0L;

        // 2. Collect role permission masks
        List<Role> memberRoles = memberRoleRepository.findRolesByMember(userId, serverId);

        // 3. ADMINISTRATOR shortcut
        boolean isAdmin = memberRoles.stream()
                .anyMatch(r -> has(r.getNrPermissions(), Permissions.ADMINISTRATOR));
        if (isAdmin) return ~0L;

        // 4. Base permissions
        long base = server.getNrPermissions();
        for (Role role : memberRoles) {
            base |= role.getNrPermissions();
        }

        // 5 & 6. Channel overrides — skip entirely when no channelId supplied
        //        (server-level permission checks pass null and must not be
        //         contaminated by channel-specific allow/deny bits)
        if (channelId != null) {
            List<UUID> roleIds = memberRoles.stream().map(Role::getIdRole).toList();
            List<PermissionOverride> roleOverrides =
                    overrideRepository.findByChannelAndRoles(channelId, roleIds);

            for (PermissionOverride ov : roleOverrides) {
                base &= ~ov.getNrDeny();
                base |=  ov.getNrAllow();
            }

            // Re-apply member override correctly (captured lambda limitation workaround)
            var memberOv = overrideRepository.findByChannelAndUser(channelId, userId);
            if (memberOv.isPresent()) {
                PermissionOverride ov = memberOv.get();
                base &= ~ov.getNrDeny();
                base |=  ov.getNrAllow();
            }
        }

        return base;
    }

    public boolean can(UUID userId, UUID channelId, UUID serverId, long permission) {
        return has(computeEffective(userId, channelId, serverId), permission);
    }

    public static boolean has(long mask, long permission) {
        return (mask & permission) == permission;
    }
}