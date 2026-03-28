package com.main.accord.domain.dm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FriendshipRepository extends JpaRepository<Friendship, Friendship.FriendshipId> {

    // Pending incoming requests (someone sent a request TO this user)
    @Query("""
        SELECT f FROM Friendship f
        WHERE (f.idUserA = :userId OR f.idUserB = :userId)
          AND f.stStatus = com.main.accord.domain.dm.FriendStatus.pending
          AND NOT (f.idRequester = :userId)
    """)
        List<Friendship> findIncomingRequests(@Param("userId") UUID userId);
    // All friendships for a user regardless of which side they're on
    @Query("""
        SELECT f FROM Friendship f
        WHERE (f.idUserA = :userId OR f.idUserB = :userId)
          AND f.stStatus = com.main.accord.domain.dm.FriendStatus.accepted
    """)
        List<Friendship> findAcceptedByUser(@Param("userId") UUID userId);

    // Pending outgoing requests (this user sent to someone else)
    @Query("""
        SELECT f FROM Friendship f
        WHERE (f.idUserA = :userId OR f.idUserB = :userId)
          AND f.stStatus = com.main.accord.domain.dm.FriendStatus.pending
          AND f.idRequester = :userId
    """)
        List<Friendship> findOutgoingRequests(@Param("userId") UUID userId);

    // Canonical lookup — always pass the smaller UUID as userA
    @Query("""
        SELECT f FROM Friendship f
        WHERE (f.idUserA = :userA AND f.idUserB = :userB)
           OR (f.idUserA = :userB AND f.idUserB = :userA)
    """)
        Optional<Friendship> findBetween(@Param("userA") UUID userA, @Param("userB") UUID userB);

    @Query("""
    SELECT f FROM Friendship f
    WHERE f.stStatus = com.main.accord.domain.dm.FriendStatus.accepted
      AND ((f.idUserA = :userId AND f.idUserB IN :otherIds)
        OR (f.idUserB = :userId AND f.idUserA IN :otherIds))
""")
    List<Friendship> findAcceptedWithAny(
            @Param("userId") UUID userId,
            @Param("otherIds") List<UUID> otherIds
    );
}