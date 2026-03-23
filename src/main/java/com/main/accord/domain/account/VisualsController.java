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
public class VisualsController {

    private final VisualsService visualsService;

    // GET /api/v1/users/{userId}/visuals
    @GetMapping("/{userId}/visuals")
    public ResponseEntity<ApiResponse<Visuals>> getVisuals(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                visualsService.getVisuals(userId)
        ));
    }

    // PATCH /api/v1/users/@me/visuals
    @PatchMapping("/@me/visuals")
    public ResponseEntity<ApiResponse<Visuals>> updateVisuals(
            @RequestBody VisualsService.UpdateVisualsRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                visualsService.updateVisuals(principal.userId(), req)
        ));
    }

    @GetMapping("/by-id/{userId}/visuals")
    public ResponseEntity<ApiResponse<Visuals>> getVisualsByUserId(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                visualsService.getVisuals(userId)
        ));
    }
}