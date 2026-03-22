package com.main.accord.domain.dm;

import com.main.accord.common.ApiResponse;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendshipController {

    private final FriendshipService friendshipService;

    // GET /api/v1/friends — accepted friends list
    @GetMapping
    public ResponseEntity<ApiResponse<List<Friendship>>> getFriends(
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                friendshipService.getFriends(principal.userId())
        ));
    }

    // GET /api/v1/friends/requests/incoming
    @GetMapping("/requests/incoming")
    public ResponseEntity<ApiResponse<List<Friendship>>> getIncoming(
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                friendshipService.getIncomingRequests(principal.userId())
        ));
    }

    // GET /api/v1/friends/requests/outgoing
    @GetMapping("/requests/outgoing")
    public ResponseEntity<ApiResponse<List<Friendship>>> getOutgoing(
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                friendshipService.getOutgoingRequests(principal.userId())
        ));
    }

    // POST /api/v1/friends/{userId} — send request
    @PostMapping("/{userId}")
    public ResponseEntity<ApiResponse<Friendship>> sendRequest(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                friendshipService.sendRequest(principal.userId(), userId)
        ));
    }

    // PUT /api/v1/friends/{userId}/accept
    @PutMapping("/{userId}/accept")
    public ResponseEntity<ApiResponse<Friendship>> accept(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                friendshipService.acceptRequest(principal.userId(), userId)
        ));
    }

    // DELETE /api/v1/friends/{userId}/decline — decline or cancel
    @DeleteMapping("/{userId}/decline")
    public ResponseEntity<ApiResponse<Void>> decline(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        friendshipService.declineOrCancel(principal.userId(), userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // DELETE /api/v1/friends/{userId} — remove friend
    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<Void>> remove(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        friendshipService.removeFriend(principal.userId(), userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // POST /api/v1/friends/{userId}/block
    @PostMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<Friendship>> block(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                friendshipService.block(principal.userId(), userId)
        ));
    }

    // DELETE /api/v1/friends/{userId}/block
    @DeleteMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<Void>> unblock(
            @PathVariable UUID userId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        friendshipService.unblock(principal.userId(), userId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}