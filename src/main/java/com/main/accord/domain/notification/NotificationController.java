package com.main.accord.domain.notification;

import com.main.accord.common.ApiResponse;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService   notificationService;
    private final NotificationRepository notificationRepository;

    // GET /api/v1/notifications
    @GetMapping
    public ResponseEntity<ApiResponse<List<Notification>>> getAll(
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                notificationRepository.findAllByUser(principal.userId())
        ));
    }

    // GET /api/v1/notifications/unread
    @GetMapping("/unread")
    public ResponseEntity<ApiResponse<List<Notification>>> getUnread(
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                notificationRepository.findUnreadByUser(principal.userId())
        ));
    }

    // PATCH /api/v1/notifications/{notifId}/read
    @PatchMapping("/{notifId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(
            @PathVariable UUID notifId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        notificationService.markRead(notifId, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // PATCH /api/v1/notifications/read-all
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @AuthenticationPrincipal AccordPrincipal principal) {
        notificationService.markAllRead(principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}