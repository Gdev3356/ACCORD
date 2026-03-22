package com.main.accord.domain.server;

import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServerService {

    private final ServerRepository   serverRepository;
    private final MemberRepository   memberRepository;
    private final RoleRepository     roleRepository;
    private final InviteRepository   inviteRepository;
    private final PermissionService  permissionService;

    @Transactional
    public Server createServer(UUID ownerId, String name) {
        Server server = serverRepository.save(
                Server.builder()
                        .idOwner(ownerId)
                        .dsName(name)
                        .nrPermissions(
                                Permissions.VIEW_CHANNELS |
                                        Permissions.SEND_MESSAGES |
                                        Permissions.READ_MESSAGE_HISTORY |
                                        Permissions.ATTACH_FILES |
                                        Permissions.EMBED_LINKS
                        )
                        .build()
        );
        // Auto-join owner as a member
        memberRepository.save(
                Member.builder()
                        .idServer(server.getIdServer())
                        .idUser(ownerId)
                        .build()
        );
        return server;
    }

    @Transactional
    public void kickMember(UUID requesterId, UUID serverId, UUID targetId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found"));

        if (server.getIdOwner().equals(targetId)) {
            throw new ForbiddenException("Cannot kick the server owner.");
        }
        if (!permissionService.can(requesterId, null, serverId, Permissions.KICK_MEMBERS)) {
            throw new ForbiddenException("You don't have permission to kick members.");
        }
        memberRepository.deleteByIdServerAndIdUser(serverId, targetId);
    }

    @Transactional
    public void banMember(UUID requesterId, UUID serverId, UUID targetId, String reason) {
        // TODO: check BAN_MEMBERS permission, insert SV_BAN, kick from server
    }

    public Invite joinByInvite(UUID userId, String inviteCode) {
        // TODO: validate invite code, check expiry, increment use count, add member
        return null;
    }
}