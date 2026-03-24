package com.main.accord.domain.dm;

import com.main.accord.domain.message.ReactionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DmReactionRepository extends JpaRepository<DmReaction, ReactionId> {

    @Query("""
        SELECT r.dsEmoji, COUNT(r),
               SUM(CASE WHEN r.idUser = :callerId THEN 1 ELSE 0 END)
        FROM DmReaction r
        WHERE r.idMessage IN :messageIds
        GROUP BY r.idMessage, r.dsEmoji
        """) // ← you'll want idMessage in SELECT too, same as before
    List<Object[]> countByEmojiForUserBatch(
            @Param("messageIds") List<UUID> messageIds,
            @Param("callerId")   UUID callerId
    );

    boolean existsByIdMessageAndIdUserAndDsEmoji(UUID idMessage, UUID idUser, String dsEmoji);

    void deleteByIdMessageAndIdUserAndDsEmoji(UUID idMessage, UUID idUser, String dsEmoji);
}