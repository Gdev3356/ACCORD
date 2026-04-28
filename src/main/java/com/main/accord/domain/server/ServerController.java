package com.main.accord.domain.server;

import com.main.accord.common.ApiResponse;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import com.main.accord.security.AccordPrincipal;
import com.main.accord.common.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/servers")
@RequiredArgsConstructor
public class ServerController {

    private final ServerService serverService;
    private final PermissionService permissionService;

    // GET /api/v1/servers
    @GetMapping
    public ResponseEntity<ApiResponse<List<Server>>> getMyServers(
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                serverService.getMyServers(principal.userId())
        ));
    }

    // GET /api/v1/servers/{serverId}
    @GetMapping("/{serverId}")
    public ResponseEntity<ApiResponse<Server>> getServer(
            @PathVariable UUID serverId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                serverService.getServer(serverId, principal.userId())
        ));
    }

    // POST /api/v1/servers
    @PostMapping
    public ResponseEntity<ApiResponse<Server>> createServer(
            @RequestBody CreateServerRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                serverService.createServer(principal.userId(), req.name())
        ));
    }

    // PATCH /api/v1/servers/{serverId}
    @PatchMapping("/{serverId}")
    public ResponseEntity<ApiResponse<Server>> updateServer(
            @PathVariable UUID serverId,
            @RequestBody ServerService.UpdateServerRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!permissionService.can(principal.userId(), null, serverId, Permissions.MANAGE_SERVER)) {
            throw new ForbiddenException("You need MANAGE_SERVER permission to update server settings.");
        }
        return ResponseEntity.ok(ApiResponse.ok(
                serverService.updateServer(serverId, principal.userId(), req)
        ));
    }

    // DELETE /api/v1/servers/{serverId}
    @DeleteMapping("/{serverId}")
    public ResponseEntity<ApiResponse<Void>> deleteServer(
            @PathVariable UUID serverId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!permissionService.can(principal.userId(), null, serverId, Permissions.MANAGE_SERVER)) {
            throw new ForbiddenException("You need MANAGE_SERVER permission to delete this server.");
        }
        serverService.deleteServer(serverId, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // GET /api/v1/servers/{serverId}/members
    @GetMapping("/{serverId}/members")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<Member>>> getMembers(
            @PathVariable UUID serverId,
            @AuthenticationPrincipal AccordPrincipal principal,
            org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(
                serverService.getMembers(serverId, principal.userId(), pageable)
        ));
    }
    // DELETE /api/v1/servers/{serverId}/members/{userId}  (kick)
    @DeleteMapping("/{serverId}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> kickMember(
            @PathVariable UUID serverId,
            @PathVariable UUID userId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!permissionService.can(principal.userId(), null, serverId, Permissions.KICK_MEMBERS)) {
            throw new ForbiddenException("You need KICK_MEMBERS permission to kick members.");
        }

        String reason = body != null ? body.get("reason") : null;
        serverService.kickMember(principal.userId(), serverId, userId, reason);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // DELETE /api/v1/servers/{serverId}/leave
    @DeleteMapping("/{serverId}/leave")
    public ResponseEntity<ApiResponse<Void>> leaveServer(
            @PathVariable UUID serverId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        serverService.leaveServer(serverId, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // POST /api/v1/invites/{code}/join  (convenience — delegates to InviteService)
    @PostMapping("/invites/{code}/join")
    public ResponseEntity<ApiResponse<Invite>> joinByInvite(
            @PathVariable String code,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                serverService.joinByInvite(principal.userId(), code)
        ));
    }

    @PatchMapping("/{serverId}/members/{userId}")
    public ResponseEntity<ApiResponse<Member>> updateMember(
            @PathVariable UUID serverId,
            @PathVariable UUID userId,
            @RequestBody ServerService.UpdateMemberRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!permissionService.can(principal.userId(), null, serverId, Permissions.MUTE_MEMBERS)) {
            throw new ForbiddenException("You need MUTE_MEMBERS permission to mute members.");
        }
        return ResponseEntity.ok(ApiResponse.ok(
                serverService.updateMember(principal.userId(), serverId, userId, req)
        ));
    }

    @PostMapping("/{serverId}/members/{userId}/timeout")
    public ResponseEntity<ApiResponse<Member>> timeoutMember(
            @PathVariable UUID serverId,
            @PathVariable UUID userId,
            @RequestBody TimeoutRequest request,
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!permissionService.can(principal.userId(), null, serverId, Permissions.TIMEOUT_MEMBERS)) {
            throw new ForbiddenException("You need TIMEOUT_MEMBERS permission to timeout members.");
        }

        return ResponseEntity.ok(ApiResponse.ok(
                serverService.timeoutMember(principal.userId(), serverId, userId,
                        request.durationMinutes(), request.reason())
        ));
    }

    @DeleteMapping("/{serverId}/members/{userId}/timeout")
    public ResponseEntity<ApiResponse<Void>> removeTimeout(
            @PathVariable UUID serverId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!permissionService.can(principal.userId(), null, serverId, Permissions.TIMEOUT_MEMBERS)) {
            throw new ForbiddenException("You need TIMEOUT_MEMBERS permission to remove timeouts.");
        }

        serverService.removeTimeout(principal.userId(), serverId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    public record TimeoutRequest(int durationMinutes, String reason) {}

    @GetMapping("/{serverId}/permissions")
    public ResponseEntity<ApiResponse<Long>> getMyPermissions(
            @PathVariable UUID serverId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                serverService.getMyPermissions(principal.userId(), serverId)
        ));
    }

    public record CreateServerRequest(String name) {}

    // GET /api/v1/servers/{serverId}/members/{userId}
    @GetMapping("/{serverId}/members/{userId}")
    public ResponseEntity<ApiResponse<Member>> getMember(
            @PathVariable UUID serverId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal AccordPrincipal principal) {

        // Check if requester is a member of the server
        if (!serverService.isMember(serverId, principal.userId())) {
            throw new ForbiddenException("You are not a member of this server.");
        }

        Member member = serverService.getMember(serverId, userId);
        return ResponseEntity.ok(ApiResponse.ok(member));
    }

    @GetMapping("/summaries")
    public ResponseEntity<ApiResponse<List<ServerSummaryDto>>> getSummaries(
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(serverService.getServerSummaries(principal.userId())));
    }

    @PatchMapping("/{serverId}/members/{userId}/nickname")
    public ResponseEntity<ApiResponse<Member>> changeNickname(
            @PathVariable UUID serverId,
            @PathVariable UUID userId,
            @RequestBody NicknameRequest request,
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!permissionService.can(principal.userId(), null, serverId, Permissions.MANAGE_NICKNAMES)) {
            throw new ForbiddenException("You need MANAGE_NICKNAMES permission to change nicknames.");
        }

        return ResponseEntity.ok(ApiResponse.ok(
                serverService.changeNickname(serverId, userId, principal.userId(), request.nickname())
        ));
    }

    public record NicknameRequest(String nickname) {}
}