package com.main.accord.domain.server;

import com.main.accord.common.ApiResponse;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/servers")
@RequiredArgsConstructor
public class ServerController {

    private final ServerService serverService;

    @PostMapping
    public ResponseEntity<ApiResponse<Server>> createServer(
            @RequestBody CreateServerRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                serverService.createServer(principal.userId(), req.name())
        ));
    }

    @DeleteMapping("/{serverId}/members/{userId}")
    public ResponseEntity<ApiResponse<Void>> kickMember(
            @PathVariable UUID serverId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal AccordPrincipal principal) {

        serverService.kickMember(principal.userId(), serverId, userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/invites/{code}/join")
    public ResponseEntity<ApiResponse<Invite>> joinByInvite(
            @PathVariable String code,
            @AuthenticationPrincipal AccordPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                serverService.joinByInvite(principal.userId(), code)
        ));
    }

    public record CreateServerRequest(String name) {}
}
