package com.main.accord.domain.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReactionRepository extends JpaRepository<Reaction, ReactionId> {

    List<Reaction> findByIdMessage(UUID messageId);

    boolean existsByIdMessageAndIdUserAndDsEmoji(UUID messageId, UUID userId, String emoji);

    void deleteByIdMessageAndIdUserAndDsEmoji(UUID messageId, UUID userId, String emoji);

    // ── Typed projection — replaces all Object[] overloads ───────────────────

    /**
     * Per-emoji counts for a single message, with a flag for the caller's own reaction.
     * Replaces the old countByEmoji / countByEmojiForUser Object[] queries.
     */
    @Query("""
        SELECT r.dsEmoji          AS emoji,
               COUNT(r)           AS count,
               SUM(CASE WHEN r.idUser = :callerId THEN 1 ELSE 0 END) > 0 AS reactedByMe
        FROM Reaction r
        WHERE r.idMessage = :messageId
        GROUP BY r.dsEmoji
    """)
    List<ReactionCount> countByEmojiForUser(
            @Param("messageId") UUID messageId,
            @Param("callerId")  UUID callerId);

    /**
     * Batch version — fetches emoji counts for many messages in one query.
     * Replaces the old countByEmojiForUserBatch Object[] query.
     */
    @Query("""
        SELECT r.idMessage         AS messageId,
               r.dsEmoji           AS emoji,
               COUNT(r)            AS count,
               SUM(CASE WHEN r.idUser = :callerId THEN 1 ELSE 0 END) > 0 AS reactedByMe
        FROM Reaction r
        WHERE r.idMessage IN :messageIds
        GROUP BY r.idMessage, r.dsEmoji
    """)
    List<ReactionCount> countByEmojiForUserBatch(
            @Param("messageIds") List<UUID> messageIds,
            @Param("callerId")   UUID callerId);

    // ── Projection interface ──────────────────────────────────────────────────

    interface ReactionCount {
        UUID    getMessageId();   // null for single-message overload — that's fine
        String  getEmoji();
        Long    getCount();
        Boolean getReactedByMe();
    }
}