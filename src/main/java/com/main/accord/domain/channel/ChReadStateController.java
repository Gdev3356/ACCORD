package com.main.accord.domain.channel;

import com.main.accord.common.ApiResponse;
import com.main.accord.domain.message.MessageService;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class ChReadStateController {

    private final MessageService messageService;

    public record MarkReadRequest(UUID lastMessageId) {}

    /** POST /api/v1/channels/{channelId}/read */
    @PostMapping("/api/v1/channels/{channelId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable UUID channelId,
            @RequestBody MarkReadRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        messageService.markRead(channelId, principal.userId(), req.lastMessageId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * GET /api/v1/servers/{serverId}/unread
     * Returns { channelId: unreadCount } for every channel with at least 1 unread.
     * Channels absent from the map have 0 unread.
     */
    @GetMapping("/api/v1/servers/{serverId}/unread")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getUnread(
            @PathVariable UUID serverId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        Map<String, Long> counts = messageService
                .getUnreadCounts(serverId, principal.userId())
                .entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey().toString(),
                        Map.Entry::getValue
                ));
        return ResponseEntity.ok(ApiResponse.ok(counts));
    }
}