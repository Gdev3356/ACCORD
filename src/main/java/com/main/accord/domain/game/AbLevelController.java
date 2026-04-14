package com.main.accord.domain.game;

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
@RequestMapping("/api/v1/ab/levels")
@RequiredArgsConstructor
public class AbLevelController {

    private final AbLevelService levelService;

    // ── Level CRUD ────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/ab/levels?page=0
     *
     * FIX: passes a page number to the service so the query is bounded.
     *      Defaults to page 0 if the param is absent.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AbLevelSummary>>> list(
            @RequestParam(defaultValue = "0") int page) {
        return ResponseEntity.ok(ApiResponse.ok(levelService.getPublished(page)));
    }

    /** GET /api/v1/ab/levels/{levelId} */
    @GetMapping("/{levelId}")
    public ResponseEntity<ApiResponse<AbLevel>> get(@PathVariable UUID levelId) {
        return ResponseEntity.ok(ApiResponse.ok(levelService.getById(levelId)));
    }

    /** GET /api/v1/ab/levels/by-creator/{userId} */
    @GetMapping("/by-creator/{userId}")
    public ResponseEntity<ApiResponse<List<AbLevel>>> byCreator(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(levelService.getByCreator(userId)));
    }

    /** POST /api/v1/ab/levels — save draft */
    @PostMapping
    public ResponseEntity<ApiResponse<AbLevel>> create(
            @RequestBody AbLevelService.SaveLevelRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(levelService.saveDraft(principal.userId(), req)));
    }

    /** PATCH /api/v1/ab/levels/{levelId} — update draft */
    @PatchMapping("/{levelId}")
    public ResponseEntity<ApiResponse<AbLevel>> update(
            @PathVariable UUID levelId,
            @RequestBody AbLevelService.SaveLevelRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(levelService.updateDraft(principal.userId(), levelId, req)));
    }

    /** POST /api/v1/ab/levels/{levelId}/publish */
    @PostMapping("/{levelId}/publish")
    public ResponseEntity<ApiResponse<AbLevel>> publish(
            @PathVariable UUID levelId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(levelService.publish(principal.userId(), levelId)));
    }

    /** DELETE /api/v1/ab/levels/{levelId} */
    @DeleteMapping("/{levelId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID levelId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        levelService.delete(principal.userId(), levelId, principal.isAdmin());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Scores ────────────────────────────────────────────────────────────────

    /** POST /api/v1/ab/levels/{levelId}/scores */
    @PostMapping("/{levelId}/scores")
    public ResponseEntity<ApiResponse<AbScore>> submitScore(
            @PathVariable UUID levelId,
            @RequestBody ScoreRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                levelService.submitScore(principal.userId(), levelId, req.score(), req.stars())));
    }

    /** GET /api/v1/ab/levels/{levelId}/leaderboard */
    @GetMapping("/{levelId}/leaderboard")
    public ResponseEntity<ApiResponse<List<AbScore>>> leaderboard(@PathVariable UUID levelId) {
        return ResponseEntity.ok(ApiResponse.ok(levelService.getLeaderboard(levelId)));
    }

    /** GET /api/v1/ab/levels/{levelId}/my-score */
    @GetMapping("/{levelId}/my-score")
    public ResponseEntity<ApiResponse<AbScore>> getMyScore(
            @PathVariable UUID levelId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                levelService.getUserScore(principal.userId(), levelId)));
    }

    // ── Votes ─────────────────────────────────────────────────────────────────

    /** POST /api/v1/ab/levels/{levelId}/vote */
    @PostMapping("/{levelId}/vote")
    public ResponseEntity<ApiResponse<Void>> vote(
            @PathVariable UUID levelId,
            @RequestBody VoteRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        levelService.vote(principal.userId(), levelId, req.upvote());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Comments ──────────────────────────────────────────────────────────────

    /** GET /api/v1/ab/levels/{levelId}/comments */
    @GetMapping("/{levelId}/comments")
    public ResponseEntity<ApiResponse<List<AbLevelComment>>> comments(@PathVariable UUID levelId) {
        return ResponseEntity.ok(ApiResponse.ok(levelService.getComments(levelId)));
    }

    /** POST /api/v1/ab/levels/{levelId}/comments */
    @PostMapping("/{levelId}/comments")
    public ResponseEntity<ApiResponse<AbLevelComment>> addComment(
            @PathVariable UUID levelId,
            @RequestBody CommentRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                levelService.addComment(principal.userId(), levelId, req.content())));
    }

    /** DELETE /api/v1/ab/levels/comments/{commentId} */
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable UUID commentId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        levelService.deleteComment(principal.userId(), commentId, principal.isAdmin());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    /** POST /api/v1/ab/levels/{levelId}/report */
    @PostMapping("/{levelId}/report")
    public ResponseEntity<ApiResponse<AbLevelReport>> report(
            @PathVariable UUID levelId,
            @RequestBody(required = false) ReportRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                levelService.reportLevel(principal.userId(), levelId, req != null ? req.reason() : null)));
    }

    /** GET /api/v1/ab/levels/reports/pending — admin only */
    @GetMapping("/reports/pending")
    public ResponseEntity<ApiResponse<List<AbLevelReport>>> pendingReports(
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!principal.isAdmin()) throw new ForbiddenException("Admin only.");
        return ResponseEntity.ok(ApiResponse.ok(levelService.getPendingReports()));
    }

    /** PATCH /api/v1/ab/levels/reports/{reportId} — admin only */
    @PatchMapping("/reports/{reportId}")
    public ResponseEntity<ApiResponse<AbLevelReport>> reviewReport(
            @PathVariable UUID reportId,
            @RequestBody ReviewRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!principal.isAdmin()) throw new ForbiddenException("Admin only.");
        return ResponseEntity.ok(ApiResponse.ok(
                levelService.reviewReport(principal.userId(), reportId, req.action())));
    }

    // GET /api/v1/ab/levels/{levelId}/my-vote
    @GetMapping("/{levelId}/my-vote")
    public ResponseEntity<ApiResponse<Boolean>> getMyVote(
            @PathVariable UUID levelId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                levelService.getMyVote(principal.userId(), levelId)));
    }
    // ── Request records ───────────────────────────────────────────────────────

    public record ScoreRequest(int score, short stars) {}
    public record VoteRequest(boolean upvote) {}
    public record CommentRequest(String content) {}
    public record ReportRequest(String reason) {}
    public record ReviewRequest(String action) {}
}