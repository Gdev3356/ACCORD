package com.main.accord.domain.server;

import com.main.accord.common.ApiResponse;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    // GET /api/v1/servers/{serverId}/invites
    @GetMapping("/servers/{serverId}/invites")
    public ResponseEntity<ApiResponse<List<Invite>>> getInvites(
            @PathVariable UUID serverId,
            @AuthenticationPrincipal AccordPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                inviteService.getInvites(serverId, principal.userId())
        ));
    }

    // POST /api/v1/servers/{serverId}/invites
    @PostMapping("/servers/{serverId}/invites")
    public ResponseEntity<ApiResponse<Invite>> createInvite(
            @PathVariable UUID serverId,
            @RequestBody InviteService.CreateInviteRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                inviteService.createInvite(serverId, principal.userId(), req)
        ));
    }

    // DELETE /api/v1/servers/{serverId}/invites/{inviteId}
    @DeleteMapping("/servers/{serverId}/invites/{inviteId}")
    public ResponseEntity<ApiResponse<Void>> revokeInvite(
            @PathVariable UUID serverId,
            @PathVariable UUID inviteId,
            @AuthenticationPrincipal AccordPrincipal principal) {

        inviteService.revokeInvite(serverId, inviteId, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // GET /api/v1/invites/{code}  — public preview before joining (no auth needed)
    @GetMapping("/invites/{code}")
    public ResponseEntity<ApiResponse<Invite>> previewInvite(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(inviteService.previewInvite(code)));
    }

    // POST /api/v1/invites/{code}/join  — requires auth
    @PostMapping("/invites/{code}/join")
    public ResponseEntity<ApiResponse<Invite>> joinByCode(
            @PathVariable String code,
            @AuthenticationPrincipal AccordPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                inviteService.joinByCode(principal.userId(), code)
        ));
    }
}