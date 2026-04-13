package com.main.accord.domain.game;

import com.main.accord.common.ApiResponse;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/games/{gameSlug}/settings")
@RequiredArgsConstructor
public class PlayerSettingsController {

    private final PlayerSettingsService playerSettingsService;

    // GET /api/v1/games/absettings
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSettings(
            @PathVariable String gameSlug,
            @AuthenticationPrincipal AccordPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                playerSettingsService.getSettings(principal.userId(), gameSlug)
        ));
    }

    // PATCH /api/v1/games/ab/settings
    @PatchMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> patchSettings(
            @PathVariable String gameSlug,
            @RequestBody Map<String, Object> patch,
            @AuthenticationPrincipal AccordPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                playerSettingsService.patchSettings(principal.userId(), gameSlug, patch)
        ));
    }
}