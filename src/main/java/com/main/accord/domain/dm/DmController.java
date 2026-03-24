package com.main.accord.domain.dm;

import com.main.accord.common.ApiResponse;
import com.main.accord.security.AccordPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dm")
@RequiredArgsConstructor
public class DmController {

    private final DmService dmService;
    private final DmReadStateRepository dmReadStateRepository;
    public record SendMessageRequest(String content, UUID replyToId) {};

    // GET /api/v1/dm — all conversations for current user
    @GetMapping
    public ResponseEntity<ApiResponse<List<Conversation>>> getConversations(
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                dmService.getConversations(principal.userId())
        ));
    }

    // POST /api/v1/dm/{userId} — open or get 1-on-1 DM with a user
    @PostMapping("/{userId}")
    public ResponseEntity<ApiResponse<Conversation>> openDm(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                dmService.openDm(principal.userId(), userId)
        ));
    }

    // POST /api/v1/dm/group — create group DM
    @PostMapping("/group")
    public ResponseEntity<ApiResponse<Conversation>> createGroup(
            @RequestBody CreateGroupRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                dmService.createGroup(principal.userId(), req.userIds(), req.name())
        ));
    }

    // GET /api/v1/dm/{conversationId}/messages
    @GetMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<List<DmMessage>>> getMessages(
            @PathVariable UUID conversationId,
            @RequestParam(required = false) UUID before,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                dmService.getMessages(conversationId, principal.userId(), before, limit)
        ));
    }

    // POST /api/v1/dm/{conversationId}/messages
    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<ApiResponse<DmMessage>> sendMessage(
            @PathVariable UUID conversationId,
            @RequestBody SendMessageRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                dmService.sendMessage(conversationId, principal.userId(), req.content(), req.replyToId())
        ));
    }

    // GET /api/v1/dm/{conversationId}/participants
    @GetMapping("/{conversationId}/participants")
    public ResponseEntity<ApiResponse<List<Participant>>> getParticipants(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                dmService.getParticipants(conversationId, principal.userId())
        ));
    }

    // POST /api/v1/dm/{conversationId}/typing
    @PostMapping("/{conversationId}/typing")
    public ResponseEntity<ApiResponse<Void>> sendTyping(
            @PathVariable UUID conversationId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        dmService.broadcastTyping(conversationId, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    public record CreateGroupRequest(List<UUID> userIds, String name) {}

    @GetMapping("/{conversationId}/messages/search")
    public ResponseEntity<ApiResponse<List<DmMessage>>> searchMessages(
            @PathVariable UUID conversationId,
            @RequestParam String q,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                dmService.searchMessages(conversationId, principal.userId(), q, limit)
        ));
    }

    public record EditMessageRequest(String content) {}

    @PatchMapping("/messages/{messageId}")
    public ResponseEntity<ApiResponse<DmMessage>> editMessage(
            @PathVariable UUID messageId,
            @RequestBody @Valid EditMessageRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        DmMessage edited = dmService.editMessage(messageId, principal.userId(), req.content());
        return ResponseEntity.ok(ApiResponse.ok(edited));
    }
    @PostMapping("/{conversationId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable UUID conversationId,
            @RequestBody MarkReadRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        dmService.markRead(conversationId, principal.userId(), req.lastMessageId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @PatchMapping("/{conversationId}/mute")
    public ResponseEntity<ApiResponse<Void>> setMuted(
            @PathVariable UUID conversationId,
            @RequestParam boolean value,
            @AuthenticationPrincipal AccordPrincipal principal) {
        dmService.setMuted(conversationId, principal.userId(), value);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<UUID>>> getUnread(
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(dmService.getUnreadConversations(principal.userId())));
    }

    public record MarkReadRequest(UUID lastMessageId) {}

    @GetMapping("/messages/{messageId}")

    public ResponseEntity<ApiResponse<DmMessage>> getMessage(
            @PathVariable UUID messageId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                dmService.getMessage(messageId, principal.userId())
        ));
    }
}