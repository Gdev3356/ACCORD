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
@RequestMapping("/api/v1/servers/{serverId}/bans")
@RequiredArgsConstructor
public class BanController {

    private final BanService banService;

    // GET /api/v1/servers/{serverId}/bans
    @GetMapping
    public ResponseEntity<ApiResponse<List<ServerBan>>> getBans(
            @PathVariable UUID serverId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                banService.getServerBans(principal.userId(), serverId)
        ));
    }

    // PUT /api/v1/servers/{serverId}/bans/{userId}
    @PutMapping("/{userId}")
    public ResponseEntity<ApiResponse<ServerBan>> ban(
            @PathVariable UUID serverId,
            @PathVariable UUID userId,
            @RequestBody(required = false) BanRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                banService.banFromServer(
                        principal.userId(), serverId, userId,
                        req != null ? req.reason() : null
                )
        ));
    }

    // DELETE /api/v1/servers/{serverId}/bans/{userId}
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> unban(
            @PathVariable UUID serverId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        banService.unbanFromServer(principal.userId(), serverId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    public record BanRequest(String reason) {}
}