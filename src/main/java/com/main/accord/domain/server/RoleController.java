package com.main.accord.domain.server;

import com.main.accord.common.ApiResponse;
import com.main.accord.common.ForbiddenException;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import com.main.accord.security.AccordPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/servers/{serverId}/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;
    private final MemberRepository memberRepository;
    private final MemberRoleRepository memberRoleRepository;
    private final PermissionService permissionService;

    // GET /api/v1/servers/{serverId}/roles
    @GetMapping
    public ResponseEntity<ApiResponse<List<Role>>> getRoles(
            @PathVariable UUID serverId,
            @AuthenticationPrincipal AccordPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                roleService.getRoles(serverId, principal.userId())
        ));
    }

    // POST /api/v1/servers/{serverId}/roles
    @PostMapping
    public ResponseEntity<ApiResponse<Role>> createRole(
            @PathVariable UUID serverId,
            @Valid @RequestBody RoleService.CreateRoleRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
            if (!permissionService.can(principal.userId(), null, serverId, Permissions.MANAGE_SERVER)) {
                throw new ForbiddenException("You need MANAGE_SERVER permission to create webhooks.");
            }
        return ResponseEntity.ok(ApiResponse.ok(
                roleService.createRole(serverId, principal.userId(), req)
        ));
    }

    // PATCH /api/v1/servers/{serverId}/roles/{roleId}
    @PatchMapping("/{roleId}")
    public ResponseEntity<ApiResponse<Role>> updateRole(
            @PathVariable UUID serverId,
            @PathVariable UUID roleId,
            @Valid @RequestBody RoleService.UpdateRoleRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
            if (!permissionService.can(principal.userId(), null, serverId, Permissions.MANAGE_SERVER)) {
                throw new ForbiddenException("You need MANAGE_SERVER permission to create webhooks.");
            }
        return ResponseEntity.ok(ApiResponse.ok(
                roleService.updateRole(serverId, roleId, principal.userId(), req)
        ));
    }

    // DELETE /api/v1/servers/{serverId}/roles/{roleId}
    @DeleteMapping("/{roleId}")
    public ResponseEntity<ApiResponse<Void>> deleteRole(
            @PathVariable UUID serverId,
            @PathVariable UUID roleId,
            @AuthenticationPrincipal AccordPrincipal principal) {
            if (!permissionService.can(principal.userId(), null, serverId, Permissions.MANAGE_SERVER)) {
                throw new ForbiddenException("You need MANAGE_SERVER permission to create webhooks.");
            }
        roleService.deleteRole(serverId, roleId, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // PUT /api/v1/servers/{serverId}/roles/{roleId}/members/{userId}
    @PutMapping("/{roleId}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> assignRole(
            @PathVariable UUID serverId,
            @PathVariable UUID roleId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal AccordPrincipal principal) {
            if (!permissionService.can(principal.userId(), null, serverId, Permissions.MANAGE_SERVER)) {
                throw new ForbiddenException("You need MANAGE_SERVER permission to create webhooks.");
            }
        roleService.assignRole(serverId, userId, roleId, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // GET /api/v1/servers/{serverId}/roles/members/{userId}
    @GetMapping("/members/{userId}")
    public ResponseEntity<ApiResponse<List<Role>>> getMemberRoles(
            @PathVariable UUID serverId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!memberRepository.existsByIdServerAndIdUser(serverId, principal.userId()))
            throw new ForbiddenException("Not a member.");
        return ResponseEntity.ok(ApiResponse.ok(
                memberRoleRepository.findRolesByMember(userId, serverId)
        ));
    }

    // DELETE /api/v1/servers/{serverId}/roles/{roleId}/members/{userId}
    @DeleteMapping("/{roleId}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> revokeRole(
            @PathVariable UUID serverId,
            @PathVariable UUID roleId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal AccordPrincipal principal) {
            if (!permissionService.can(principal.userId(), null, serverId, Permissions.MANAGE_SERVER)) {
                throw new ForbiddenException("You need MANAGE_SERVER permission to create webhooks.");
            }
        roleService.revokeRole(serverId, userId, roleId, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}