package com.main.accord.domain.message;

import com.main.accord.common.ApiResponse;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/channels/{channelId}/messages/{messageId}/reactions")
@RequiredArgsConstructor
public class ChannelReactionController {

    private final ReactionService reactionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<ReactionService.ReactionSummary>>> getReactions(
            @PathVariable UUID channelId,
            @PathVariable UUID messageId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                reactionService.getServerReactions(messageId, principal.userId())
        ));
    }

    @PutMapping("/{emoji}")
    public ResponseEntity<ApiResponse<Void>> addReaction(
            @PathVariable UUID channelId,
            @PathVariable UUID messageId,
            @PathVariable String emoji,
            @AuthenticationPrincipal AccordPrincipal principal) {
        reactionService.addServerReaction(messageId, channelId, principal.userId(), emoji);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/{emoji}")
    public ResponseEntity<ApiResponse<Void>> removeReaction(
            @PathVariable UUID channelId,
            @PathVariable UUID messageId,
            @PathVariable String emoji,
            @AuthenticationPrincipal AccordPrincipal principal) {
        reactionService.removeServerReaction(messageId, channelId, principal.userId(), emoji);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}