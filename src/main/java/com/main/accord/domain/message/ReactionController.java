package com.main.accord.domain.message;

import com.main.accord.common.ApiResponse;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dm/messages/{messageId}/reactions")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionService reactionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReactionService.ReactionSummary>>> getReactions(
            @PathVariable UUID messageId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                reactionService.getReactions(messageId, principal.userId())));
    }

    @PutMapping("/{emoji}")
    public ResponseEntity<ApiResponse<Void>> addReaction(
            @PathVariable UUID messageId,
            @PathVariable String emoji,
            @AuthenticationPrincipal AccordPrincipal principal) {
        reactionService.addReaction(messageId, principal.userId(), emoji);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/{emoji}")
    public ResponseEntity<ApiResponse<Void>> removeReaction(
            @PathVariable UUID messageId,
            @PathVariable String emoji,
            @AuthenticationPrincipal AccordPrincipal principal) {
        reactionService.removeReaction(messageId, principal.userId(), emoji);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PostMapping("/api/v1/dm/messages/reactions/batch")
    public ResponseEntity<ApiResponse<Map<UUID, List<ReactionService.ReactionSummary>>>> batchReactions(
            @RequestBody  BatchReactionsRequest request,
            @AuthenticationPrincipal AccordPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                reactionService.getReactionsBatch(request.messageIds(), principal.userId())));
    }

    public record BatchReactionsRequest(List<UUID> messageIds) {}
}