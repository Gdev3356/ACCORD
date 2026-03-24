package com.main.accord.domain.cosmetic;

import com.main.accord.common.ApiResponse;
import com.main.accord.common.ForbiddenException;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cosmetics")
@RequiredArgsConstructor
public class CosmeticController {

    private final CosmeticService cosmeticService;

    // Public — anyone can browse the catalog
    @GetMapping
    public ResponseEntity<ApiResponse<List<Cosmetic>>> getAll(
            @RequestParam(required = false) Cosmetic.CosmeticType type) {
        List<Cosmetic> result = type != null
                ? cosmeticService.getByType(type)
                : cosmeticService.getAll();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // Admin only — create a new cosmetic
    @PostMapping
    public ResponseEntity<ApiResponse<Cosmetic>> create(
            @RequestBody CosmeticService.CreateCosmeticRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!principal.isAdmin()) throw new ForbiddenException("Admin only.");
        return ResponseEntity.ok(ApiResponse.ok(cosmeticService.create(req)));
    }

    // Admin only — toggle active/inactive
    @PatchMapping("/{id}/active")
    public ResponseEntity<ApiResponse<Cosmetic>> setActive(
            @PathVariable UUID id,
            @RequestParam boolean value,
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!principal.isAdmin()) throw new ForbiddenException("Admin only.");
        return ResponseEntity.ok(ApiResponse.ok(cosmeticService.setActive(id, value)));
    }
}