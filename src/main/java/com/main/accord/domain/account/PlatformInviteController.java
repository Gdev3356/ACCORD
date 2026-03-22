package com.main.accord.domain.account;

import com.main.accord.common.ApiResponse;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/platform-invites")
@RequiredArgsConstructor
public class PlatformInviteController {

    private final PlatformInviteService platformInviteService;

    // GET /api/v1/platform-invites — my invites
    @GetMapping
    public ResponseEntity<ApiResponse<List<PlatformInvite>>> getMyInvites(
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                platformInviteService.getMyInvites(principal.userId())
        ));
    }

    // POST /api/v1/platform-invites — create a new invite
    @PostMapping
    public ResponseEntity<ApiResponse<PlatformInvite>> createInvite(
            @RequestBody(required = false) CreateInviteRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                platformInviteService.createInvite(
                        principal.userId(),
                        req != null ? req.expiresInDays() : null
                )
        ));
    }

    public record CreateInviteRequest(Integer expiresInDays) {}
}