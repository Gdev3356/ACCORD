package com.main.accord.domain.dm;

import com.main.accord.domain.message.ReactionId;
import com.main.accord.domain.message.ReactionRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DmReactionRepository extends JpaRepository<DmReaction, ReactionId> {

    boolean existsByIdMessageAndIdUserAndDsEmoji(UUID idMessage, UUID idUser, String dsEmoji);

    void deleteByIdMessageAndIdUserAndDsEmoji(UUID idMessage, UUID idUser, String dsEmoji);

    // ── Typed projection — replaces Object[] batch query ─────────────────────

    /**
     * Batch emoji counts for DM messages, with per-caller reaction flag.
     *
     * Previously returned List<Object[]> with an acknowledged missing idMessage
     * column. Now fully typed via the shared ReactionRepository.ReactionCount
     * projection (reused to avoid duplication).
     */
    @Query("""
        SELECT r.idMessage         AS messageId,
               r.dsEmoji           AS emoji,
               COUNT(r)            AS count,
               SUM(CASE WHEN r.idUser = :callerId THEN 1 ELSE 0 END) > 0 AS reactedByMe
        FROM DmReaction r
        WHERE r.idMessage IN :messageIds
        GROUP BY r.idMessage, r.dsEmoji
    """)
    List<ReactionRepository.ReactionCount> countByEmojiForUserBatch(
            @Param("messageIds") List<UUID> messageIds,
            @Param("callerId")   UUID callerId);
}