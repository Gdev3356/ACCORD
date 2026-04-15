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
@RequestMapping("/api/v1/channels/{channelId}/messages")
@RequiredArgsConstructor
public class ChannelReactionBatchController {

    private final ReactionService reactionService;

    @PostMapping("/reactions/batch")
    public ResponseEntity<ApiResponse<Map<UUID, List<ReactionService.ReactionSummary>>>> batch(
            @PathVariable UUID channelId,
            @RequestBody BatchRequest request,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                reactionService.getServerReactionsBatch(request.messageIds(), principal.userId())
        ));
    }

    public record BatchRequest(List<UUID> messageIds) {}
}