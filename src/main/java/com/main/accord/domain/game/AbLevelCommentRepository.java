package com.main.accord.domain.game;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface AbLevelCommentRepository extends JpaRepository<AbLevelComment, UUID> {

    @Query("SELECT c FROM AbLevelComment c WHERE c.idLevel = :levelId AND c.stDeleted = false ORDER BY c.dtCreated DESC")
    List<AbLevelComment> findByLevel(@Param("levelId") UUID levelId);

    // FIX: needed for the ab.critic achievement (10 comments threshold)
    long countByIdUserAndStDeletedFalse(UUID idUser);
}