package com.main.accord.domain.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface ReactionRepository extends JpaRepository<Reaction, ReactionId> {

    List<Reaction> findByIdMessage(UUID messageId);

    // Returns each emoji + count for a message
    @Query("SELECT r.dsEmoji, COUNT(r) FROM Reaction r WHERE r.idMessage = :messageId GROUP BY r.dsEmoji")
    List<Object[]> countByEmoji(UUID messageId);

    boolean existsByIdMessageAndIdUserAndDsEmoji(UUID messageId, UUID userId, String emoji);

    void deleteByIdMessageAndIdUserAndDsEmoji(UUID messageId, UUID userId, String emoji);
}