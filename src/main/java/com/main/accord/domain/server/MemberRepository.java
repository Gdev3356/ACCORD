package com.main.accord.domain.server;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, Member.MemberId> {

    /**
     * Paginated member list — replaces the unbounded findByIdServer(UUID).
     * The old query returned every member with no cap, which would OOM on
     * large servers. Use Page<Member> at the call site and pass
     * PageRequest.of(page, size, Sort.by("dtJoined").descending()).
     */
    Page<Member> findByIdServer(UUID serverId, Pageable pageable);

    /**
     * Kept as an internal utility for small, controlled use-cases
     * (e.g. permission checks, broadcasting). Do NOT expose this via a
     * controller endpoint without pagination.
     */
    List<Member> findByIdServer(UUID serverId);

    Optional<Member> findByIdServerAndIdUser(UUID serverId, UUID userId);

    boolean existsByIdServerAndIdUser(UUID serverId, UUID userId);

    // Removed: findServer(UUID serverId) — Server lookups belong in ServerRepository.

    @Query("""
        SELECT COALESCE(MAX(r.nrPosition), -1) FROM Role r
        JOIN MemberRole mr ON mr.idRole = r.idRole
        WHERE mr.idUser = :userId AND mr.idServer = :serverId
    """)
    short getHighestRolePosition(UUID userId, UUID serverId);

    @Query("SELECT m.idServer FROM Member m WHERE m.idUser = :userId")
    List<UUID> findServerIdsByUser(UUID userId);

    @Modifying
    @Query("DELETE FROM Member m WHERE m.idServer = :serverId AND m.idUser = :userId")
    void deleteByIdServerAndIdUser(UUID serverId, UUID userId);

    @Modifying
    @Query("UPDATE Member m SET m.stTimeout = false, m.dtTimeoutExpires = null " +
            "WHERE m.stTimeout = true AND m.dtTimeoutExpires < :now")
    int expireTimeouts(OffsetDateTime now);

    @Query("SELECT m FROM Member m WHERE m.stTimeout = true AND m.dtTimeoutExpires < :now")
    List<Member> findExpiredTimeouts(OffsetDateTime now);

    @Query("SELECT m.idUser FROM Member m WHERE m.idServer = :serverId")
    List<UUID> findUserIdsByServerId(@Param("serverId") UUID serverId);

    @Query("SELECT m1.idServer FROM Member m1 " +
            "JOIN Member m2 ON m1.idServer = m2.idServer " +
            "WHERE m1.idUser = :userA AND m2.idUser = :userB")
    List<UUID> findCommonServers(@Param("userA") UUID userA, @Param("userB") UUID userB);

    @Query("""
        SELECT DISTINCT
            CASE WHEN f.idUserA = :userId THEN f.idUserB ELSE f.idUserA END
        FROM Friendship f
        WHERE (f.idUserA = :userId OR f.idUserB = :userId)
          AND f.stStatus = com.main.accord.domain.dm.FriendStatus.accepted
    """)
    List<UUID> findFriendIds(@Param("userId") UUID userId);
}