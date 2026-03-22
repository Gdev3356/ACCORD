package com.main.accord.domain.account;

import com.main.accord.common.ApiResponse;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(
            @RequestBody AuthService.RegisterRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.register(req)));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthService.AuthResponse>> login(
            @RequestBody AuthService.LoginRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(req)));
    }

    // GET so the link in the email works directly in the browser
    @GetMapping("/verify")
    public ResponseEntity<ApiResponse<AuthService.AuthResponse>> verify(
            @RequestParam String token) {
        return ResponseEntity.ok(ApiResponse.ok(authService.verify(token)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthService.AuthResponse>> refresh(
            @RequestBody RefreshRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(req.refreshToken())));
    }

    @PostMapping("/signout")
    public ResponseEntity<ApiResponse<Void>> signOut(
            @AuthenticationPrincipal AccordPrincipal principal) {
        authService.signOut(principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    public record RefreshRequest(String refreshToken) {}
}