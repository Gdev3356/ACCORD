package com.main.accord.domain.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ReactionRepository extends JpaRepository<Reaction, ReactionId> {

    List<Reaction> findByIdMessage(UUID messageId);

    // Returns each emoji + count for a message
    @Query("SELECT r.dsEmoji, COUNT(r) FROM Reaction r WHERE r.idMessage = :messageId GROUP BY r.dsEmoji")
    List<Object[]> countByEmoji(UUID messageId);

    boolean existsByIdMessageAndIdUserAndDsEmoji(UUID messageId, UUID userId, String emoji);

    void deleteByIdMessageAndIdUserAndDsEmoji(UUID messageId, UUID userId, String emoji);

    @Query("""
    SELECT r.dsEmoji, COUNT(r), 
           SUM(CASE WHEN r.idUser = :userId THEN 1 ELSE 0 END) > 0
    FROM Reaction r
    WHERE r.idMessage = :messageId
    GROUP BY r.dsEmoji
    """)
    List<Object[]> countByEmojiForUser(UUID messageId, UUID userId);

    @Query("""
    SELECT r.idMessage, r.dsEmoji, COUNT(r),
           SUM(CASE WHEN r.idUser = :callerId THEN 1 ELSE 0 END) > 0
    FROM Reaction r
    WHERE r.idMessage IN :messageIds
    GROUP BY r.idMessage, r.dsEmoji
    """)
    List<Object[]> countByEmojiForUserBatch(
            @Param("messageIds") List<UUID> messageIds,
            @Param("callerId")   UUID callerId
    );
}