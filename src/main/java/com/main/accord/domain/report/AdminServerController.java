package com.main.accord.domain.report;

import com.main.accord.common.ApiResponse;
import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.server.*;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/servers")
@RequiredArgsConstructor
public class AdminServerController {

    private final ServerRepository  serverRepository;
    private final MemberRepository  memberRepository;
    private final ServerBanRepository serverBanRepository;

    private void assertAdmin(AccordPrincipal principal) {
        if (!principal.isAdmin()) throw new ForbiddenException("Admin only.");
    }

    // GET /api/v1/admin/servers — list all servers on the platform
    @GetMapping
    public ResponseEntity<ApiResponse<List<Server>>> listAll(
            @AuthenticationPrincipal AccordPrincipal principal) {
        assertAdmin(principal);
        return ResponseEntity.ok(ApiResponse.ok(serverRepository.findAll()));
    }

    // GET /api/v1/admin/servers/{serverId}
    @GetMapping("/{serverId}")
    public ResponseEntity<ApiResponse<Server>> getServer(
            @PathVariable UUID serverId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        assertAdmin(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                serverRepository.findById(serverId)
                        .orElseThrow(() -> new NotFoundException("Server not found."))
        ));
    }

    // GET /api/v1/admin/servers/{serverId}/members
    @GetMapping("/{serverId}/members")
    public ResponseEntity<ApiResponse<List<Member>>> getMembers(
            @PathVariable UUID serverId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        assertAdmin(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                memberRepository.findByIdServer(serverId)
        ));
    }

    // GET /api/v1/admin/servers/{serverId}/bans
    @GetMapping("/{serverId}/bans")
    public ResponseEntity<ApiResponse<List<ServerBan>>> getBans(
            @PathVariable UUID serverId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        assertAdmin(principal);
        return ResponseEntity.ok(ApiResponse.ok(
                serverBanRepository.findByIdServer(serverId)
        ));
    }

    // DELETE /api/v1/admin/servers/{serverId} — force delete any server
    @DeleteMapping("/{serverId}")
    public ResponseEntity<ApiResponse<Void>> deleteServer(
            @PathVariable UUID serverId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        assertAdmin(principal);
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found."));
        serverRepository.delete(server);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // DELETE /api/v1/admin/servers/{serverId}/members/{userId} — force-kick anyone
    @DeleteMapping("/{serverId}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> kickMember(
            @PathVariable UUID serverId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        assertAdmin(principal);
        memberRepository.deleteByIdServerAndIdUser(serverId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}