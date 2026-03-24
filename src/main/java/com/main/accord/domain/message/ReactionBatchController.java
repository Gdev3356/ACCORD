package com.main.accord.domain.message;

import com.main.accord.common.ApiResponse;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dm/messages/reactions")
@RequiredArgsConstructor
public class ReactionBatchController {

    private final ReactionService reactionService;

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<Map<UUID, List<ReactionService.ReactionSummary>>>> batchReactions(
            @RequestBody BatchReactionsRequest request,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                reactionService.getReactionsBatch(request.messageIds(), principal.userId())));
    }

    public record BatchReactionsRequest(List<UUID> messageIds) {}
}