package com.main.accord.domain.report;

import com.main.accord.common.ApiResponse;
import com.main.accord.common.ForbiddenException;
import com.main.accord.domain.server.BanService;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final BanService banService;

    public record BanRequest(String reason, String expires) {}

    @PostMapping("/ban/{userId}")
    public ResponseEntity<ApiResponse<Void>> platformBan(
            @PathVariable UUID userId,
            @RequestBody BanRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!principal.isAdmin()) throw new ForbiddenException("Admin only.");
        OffsetDateTime exp = req.expires() != null ? OffsetDateTime.parse(req.expires()) : null;
        banService.platformBan(principal.userId(), userId, req.reason(), exp);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}