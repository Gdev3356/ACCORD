package com.main.accord.domain.dm;

import com.main.accord.common.AccordException;
import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.notification.NotifType;
import com.main.accord.domain.notification.Notification;
import com.main.accord.domain.notification.NotificationRepository;
import com.main.accord.websocket.ChatHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final ChatHandler          chatHandler;
    private final NotificationRepository notificationRepository;
    
    // ── Send a friend request ─────────────────────────────────────────────────

    @Transactional
    public Friendship sendRequest(UUID requesterId, UUID targetId) {
        if (requesterId.equals(targetId))
            throw new AccordException("You can't send a friend request to yourself.");

        friendshipRepository.findBetween(requesterId, targetId).ifPresent(f -> {
            switch (f.getStStatus()) {
                case accepted -> throw new AccordException("You are already friends.");
                case pending  -> throw new AccordException("A friend request already exists.");
                case blocked  -> throw new ForbiddenException("Unable to send request.");
            }
        });

        Friendship saved = friendshipRepository.save(
                Friendship.create(requesterId, targetId, requesterId)
        );

        // Persist notification so it survives missed WS events
        Notification notif = notificationRepository.save(
                Notification.builder()
                        .idUser(targetId)
                        .tpNotif(NotifType.friend_request)
                        .dsTitle("Friend Request")
                        .dsBody("Someone sent you a friend request")
                        .jsPayload(Map.of("from", requesterId.toString()))
                        .build()
        );

        // Also push real-time
        chatHandler.sendToUser(targetId, Map.of(
                "type", "FRIEND_REQUEST",
                "data", Map.of("from", requesterId)
        ));

        return saved;
    }

    // ── Accept ────────────────────────────────────────────────────────────────

    @Transactional
    public Friendship acceptRequest(UUID acceptorId, UUID requesterId) {
        Friendship f = friendshipRepository.findBetween(requesterId, acceptorId)
                .orElseThrow(() -> new NotFoundException("Friend request not found."));

        if (f.getStStatus() != FriendStatus.pending) {
            throw new AccordException("No pending request to accept.");
        }
        if (f.getIdRequester().equals(acceptorId)) {
            throw new AccordException("You can't accept your own request.");
        }

        f.setStStatus(FriendStatus.accepted);
        Friendship saved = friendshipRepository.save(f);

        // Notify the original requester
        chatHandler.sendToUser(requesterId, Map.of(
                "type", "FRIEND_ACCEPTED",
                "data", Map.of("by", acceptorId)
        ));

        return saved;
    }

    // ── Decline / cancel ──────────────────────────────────────────────────────

    @Transactional
    public void declineOrCancel(UUID userId, UUID otherId) {
        Friendship f = friendshipRepository.findBetween(userId, otherId)
                .orElseThrow(() -> new NotFoundException("Friend request not found."));

        if (f.getStStatus() != FriendStatus.pending) {
            throw new AccordException("No pending request to decline.");
        }

        friendshipRepository.delete(f);
    }

    // ── Remove friend ─────────────────────────────────────────────────────────

    @Transactional
    public void removeFriend(UUID userId, UUID otherId) {
        Friendship f = friendshipRepository.findBetween(userId, otherId)
                .orElseThrow(() -> new NotFoundException("Friendship not found."));

        if (f.getStStatus() != FriendStatus.accepted) {
            throw new AccordException("You are not friends with this user.");
        }

        friendshipRepository.delete(f);
    }

    // ── Block ─────────────────────────────────────────────────────────────────

    @Transactional
    public Friendship block(UUID blockerId, UUID targetId) {
        if (blockerId.equals(targetId)) {
            throw new AccordException("You can't block yourself.");
        }

        // If a friendship/request exists, overwrite the status to blocked
        Friendship f = friendshipRepository.findBetween(blockerId, targetId)
                .orElse(Friendship.create(blockerId, targetId, blockerId));

        f.setStStatus(FriendStatus.blocked);
        // Track who blocked — store blocker as requester for this purpose
        f.setIdRequester(blockerId);
        return friendshipRepository.save(f);
    }

    // ── Unblock ───────────────────────────────────────────────────────────────

    @Transactional
    public void unblock(UUID blockerId, UUID targetId) {
        Friendship f = friendshipRepository.findBetween(blockerId, targetId)
                .orElseThrow(() -> new NotFoundException("No block found."));

        if (f.getStStatus() != FriendStatus.blocked) {
            throw new AccordException("This user is not blocked.");
        }
        if (!f.getIdRequester().equals(blockerId)) {
            throw new ForbiddenException("You didn't block this user.");
        }

        friendshipRepository.delete(f);
    }

    public List<UUID> getFriendIds(UUID userId) {
        return friendshipRepository.findAcceptedByUser(userId)
                .stream()
                .map(f -> f.getIdUserA().equals(userId) ? f.getIdUserB() : f.getIdUserA())
                .toList();
    }
    // ── Queries ───────────────────────────────────────────────────────────────

    public List<Friendship> getFriends(UUID userId) {
        return friendshipRepository.findAcceptedByUser(userId);
    }

    public List<Friendship> getIncomingRequests(UUID userId) {
        return friendshipRepository.findIncomingRequests(userId);
    }

    public List<Friendship> getOutgoingRequests(UUID userId) {
        return friendshipRepository.findOutgoingRequests(userId);
    }
}