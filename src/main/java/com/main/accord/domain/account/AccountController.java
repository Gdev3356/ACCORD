package com.main.accord.domain.account;

import com.main.accord.common.ApiResponse;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final VisualsService visualsService;

    // GET /api/v1/users/@me — own profile
    @GetMapping("/@me")
    public ResponseEntity<ApiResponse<Account>> getMe(
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                accountService.getById(principal.userId())
        ));
    }

    // GET /api/v1/users/{handle} — public profile by handle
    @GetMapping("/{handle}")
    public ResponseEntity<ApiResponse<Account>> getByHandle(
            @PathVariable String handle) {
        return ResponseEntity.ok(ApiResponse.ok(
                accountService.getByHandle(handle)
        ));
    }

    // PATCH /api/v1/users/@me — update own profile
    @PatchMapping("/@me")
    public ResponseEntity<ApiResponse<Account>> updateMe(
            @RequestBody AccountService.UpdateProfileRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                accountService.updateProfile(principal.userId(), req)
        ));
    }

    @GetMapping("/@me/visuals")
    public ResponseEntity<ApiResponse<Visuals>> getMyVisuals(
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                visualsService.getVisuals(principal.userId())
        ));
    }

    // DELETE /api/v1/users/@me — soft delete own account
    @DeleteMapping("/@me")
    public ResponseEntity<ApiResponse<Void>> deleteMe(
            @AuthenticationPrincipal AccordPrincipal principal) {
        accountService.softDelete(principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // PATCH /api/v1/users/@me/presence
    @PatchMapping("/@me/presence")
    public ResponseEntity<ApiResponse<Account>> updatePresence(
            @RequestBody PresenceRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                accountService.updatePresence(principal.userId(), req.presence())
        ));
    }

    // PATCH /api/v1/users/@me/visuals
    @PatchMapping("/@me/visuals")
    public ResponseEntity<ApiResponse<Visuals>> updateMyVisuals(
            @RequestBody VisualsService.UpdateVisualsRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                visualsService.updateVisuals(principal.userId(), req)
        ));
    }

    public record PresenceRequest(PresenceStatus presence) {}
}