package com.main.accord.domain.dm;

import com.main.accord.common.AccordException;
import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.notification.NotifType;
import com.main.accord.domain.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FriendshipService {

    private final FriendshipRepository   friendshipRepository;
    private final NotificationService    notificationService;

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

        UUID userA = requesterId.toString().compareTo(targetId.toString()) < 0 ? requesterId : targetId;
        UUID userB = requesterId.toString().compareTo(targetId.toString()) < 0 ? targetId : requesterId;

        Friendship saved = friendshipRepository.save(
                Friendship.create(userA, userB, requesterId)
        );

        notificationService.send(targetId, NotifType.friend_request,
                "Friend Request", "Someone sent you a friend request",
                Map.of("from", requesterId.toString()));

        return saved;
    }

    @Transactional
    public Friendship acceptRequest(UUID acceptorId, UUID requesterId) {
        Friendship f = friendshipRepository.findBetween(requesterId, acceptorId)
                .orElseThrow(() -> new NotFoundException("Friend request not found."));

        if (f.getStStatus() != FriendStatus.pending)
            throw new AccordException("No pending request to accept.");
        if (f.getIdRequester().equals(acceptorId))
            throw new AccordException("You can't accept your own request.");

        f.setStStatus(FriendStatus.accepted);
        Friendship saved = friendshipRepository.save(f);

        notificationService.send(requesterId, NotifType.friend_accepted,
                "Friend Request Accepted", "Your friend request was accepted.",
                Map.of("by", acceptorId.toString()));

        return saved;
    }

    @Transactional
    public void declineOrCancel(UUID userId, UUID otherId) {
        Friendship f = friendshipRepository.findBetween(userId, otherId)
                .orElseThrow(() -> new NotFoundException("Friend request not found."));

        if (f.getStStatus() != FriendStatus.pending)
            throw new AccordException("No pending request to decline.");

        UUID requesterId = f.getIdRequester();
        boolean otherPersonSentIt = !requesterId.equals(userId);

        friendshipRepository.delete(f);

        if (otherPersonSentIt) {
            notificationService.send(requesterId, NotifType.system,
                    "Friend Request Declined", "Your friend request was declined.",
                    Map.of("by", userId.toString()));
        }
    }

    // ── Remove friend ─────────────────────────────────────────────────────────

    @Transactional
    public void removeFriend(UUID userId, UUID otherId) {
        Friendship f = friendshipRepository.findBetween(userId, otherId)
                .orElseThrow(() -> new NotFoundException("Friendship not found."));

        if (f.getStStatus() != FriendStatus.accepted)
            throw new AccordException("You are not friends with this user.");

        friendshipRepository.delete(f);
    }

    // ── Block ─────────────────────────────────────────────────────────────────

    @Transactional
    public Friendship block(UUID blockerId, UUID targetId) {
        if (blockerId.equals(targetId))
            throw new AccordException("You can't block yourself.");

        Friendship f = friendshipRepository.findBetween(blockerId, targetId)
                .orElse(Friendship.create(blockerId, targetId, blockerId));

        f.setStStatus(FriendStatus.blocked);
        f.setIdRequester(blockerId);
        return friendshipRepository.save(f);
    }

    // ── Unblock ───────────────────────────────────────────────────────────────

    @Transactional
    public void unblock(UUID blockerId, UUID targetId) {
        Friendship f = friendshipRepository.findBetween(blockerId, targetId)
                .orElseThrow(() -> new NotFoundException("No block found."));

        if (f.getStStatus() != FriendStatus.blocked)
            throw new AccordException("This user is not blocked.");
        if (!f.getIdRequester().equals(blockerId))
            throw new ForbiddenException("You didn't block this user.");

        friendshipRepository.delete(f);
    }

    public List<UUID> getFriendIds(UUID userId) {
        return friendshipRepository.findAcceptedByUser(userId)
                .stream()
                .map(f -> f.getIdUserA().equals(userId) ? f.getIdUserB() : f.getIdUserA())
                .toList();
    }

    public List<Friendship> getFriends(UUID userId)           { return friendshipRepository.findAcceptedByUser(userId); }
    public List<Friendship> getIncomingRequests(UUID userId)  { return friendshipRepository.findIncomingRequests(userId); }
    public List<Friendship> getOutgoingRequests(UUID userId)  { return friendshipRepository.findOutgoingRequests(userId); }
}