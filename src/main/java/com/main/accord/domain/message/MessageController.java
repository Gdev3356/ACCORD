package com.main.accord.domain.message;

import com.main.accord.common.ApiResponse;
import com.main.accord.security.AccordPrincipal;
import jakarta.validation.Valid;
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
public class MessageController {

    private final MessageService messageService;
    private final ReactionService reactionService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Message>>> getMessages(
            @PathVariable UUID channelId,
            @RequestParam(required = false) UUID before,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal AccordPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                messageService.getMessages(channelId, principal.userId(), before, limit)
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Message>> sendMessage(
            @PathVariable UUID channelId,
            @Valid @RequestBody SendMessageRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                messageService.sendMessage(
                        channelId,
                        principal.userId(),
                        req.content(),
                        req.replyToId(),
                        req.tpMessage(),
                        req.jsActivity()
                )
        ));
    }

    @PatchMapping("/{messageId}")
    public ResponseEntity<ApiResponse<Message>> editMessage(
            @PathVariable UUID channelId,
            @PathVariable UUID messageId,
            @Valid @RequestBody EditMessageRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                messageService.editMessage(messageId, principal.userId(), req.content())
        ));
    }

    @PostMapping("/typing")   // → POST /api/v1/channels/{channelId}/typing
    public ResponseEntity<ApiResponse<Void>> typing(
            @PathVariable UUID channelId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        messageService.broadcastTyping(channelId, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<Message>>> searchMessages(
            @PathVariable UUID channelId,
            @RequestParam String q,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                messageService.searchMessages(channelId, principal.userId(), q, limit)
        ));
    }

    @PutMapping("/{messageId}/reactions/{emoji}")
    public ResponseEntity<ApiResponse<Void>> addReaction(
            @PathVariable UUID channelId,
            @PathVariable UUID messageId,
            @PathVariable String emoji,
            @AuthenticationPrincipal AccordPrincipal principal) {
        reactionService.addServerReaction(messageId, channelId, principal.userId(), emoji);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @DeleteMapping("/{messageId}/reactions/{emoji}")
    public ResponseEntity<ApiResponse<Void>> removeReaction(
            @PathVariable UUID channelId,
            @PathVariable UUID messageId,
            @PathVariable String emoji,
            @AuthenticationPrincipal AccordPrincipal principal) {
        reactionService.removeServerReaction(messageId, channelId, principal.userId(), emoji);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/{messageId}/reactions")
    public ResponseEntity<ApiResponse<List<ReactionService.ReactionSummary>>> getReactions(
            @PathVariable UUID channelId,
            @PathVariable UUID messageId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                reactionService.getServerReactions(messageId, principal.userId())));
    }

    @PostMapping("/reactions/batch")
    public ResponseEntity<ApiResponse<Map<UUID, List<ReactionService.ReactionSummary>>>> batchReactions(
            @PathVariable UUID channelId,
            @RequestBody ReactionBatchController.BatchReactionsRequest request,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                reactionService.getServerReactionsBatch(request.messageIds(), principal.userId())));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @PathVariable UUID channelId,
            @PathVariable UUID messageId,
            @AuthenticationPrincipal AccordPrincipal principal) {

        messageService.deleteMessage(messageId, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    public record SendMessageRequest(
            String content,
            UUID replyToId,
            String tpMessage,
            java.util.Map<String, Object> jsActivity
    ) {}
    public record EditMessageRequest(@jakarta.validation.constraints.NotBlank String content) {}
}
