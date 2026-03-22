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

    // DELETE /api/v1/users/@me — soft delete own account
    @DeleteMapping("/@me")
    public ResponseEntity<ApiResponse<Void>> deleteMe(
            @AuthenticationPrincipal AccordPrincipal principal) {
        accountService.softDelete(principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}