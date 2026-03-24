package com.main.accord.domain.report;

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
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // Any user can submit a report
    @PostMapping("/{userId}")
    public ResponseEntity<ApiResponse<Report>> submit(
            @PathVariable UUID userId,
            @RequestBody(required = false) ReportRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                reportService.submit(principal.userId(), userId, req != null ? req.reason() : null)
        ));
    }

    // Admin only below
    @GetMapping
    public ResponseEntity<ApiResponse<List<Report>>> getAll(
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!principal.isAdmin()) throw new ForbiddenException("Admin only.");
        return ResponseEntity.ok(ApiResponse.ok(reportService.getAll()));
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<Report>>> getPending(
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!principal.isAdmin()) throw new ForbiddenException("Admin only.");
        return ResponseEntity.ok(ApiResponse.ok(reportService.getPending()));
    }

    @PatchMapping("/{reportId}/review")
    public ResponseEntity<ApiResponse<Report>> review(
            @PathVariable UUID reportId,
            @RequestBody ReviewRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!principal.isAdmin()) throw new ForbiddenException("Admin only.");
        return ResponseEntity.ok(ApiResponse.ok(
                reportService.review(principal.userId(), reportId, req.action())
        ));
    }

    public record ReportRequest(String reason) {}
    public record ReviewRequest(String action) {} // "actioned" or "dismissed"
}