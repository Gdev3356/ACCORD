package com.main.accord.domain.game;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface AbLevelRepository extends JpaRepository<AbLevel, UUID> {

    //      Accepts Pageable so the service can enforce a page size and avoid
    //      loading every level into memory on Render.com's 512 MB free tier.
    @Query("""
    SELECT new com.main.accord.domain.game.AbLevelSummary(
        l.idLevel, l.idCreator, l.dsName, l.dsDesc,
        l.nrParScore, l.stPublished, l.stVerified, l.stDeleted,
        l.dtCreated,
        (SELECT COUNT(v) FROM AbLevelVote v WHERE v.idLevel = l.idLevel AND v.stUpvote = true),
        (SELECT COUNT(v) FROM AbLevelVote v WHERE v.idLevel = l.idLevel AND v.stUpvote = false),
        (SELECT COUNT(DISTINCT s.idUser) FROM AbScore s WHERE s.idLevel = l.idLevel),
        (SELECT COUNT(c) FROM AbLevelComment c WHERE c.idLevel = l.idLevel AND c.stDeleted = false)
    )
    FROM AbLevel l
    WHERE l.stPublished = true AND l.stDeleted = false
    ORDER BY l.dtCreated DESC
    """)
    List<AbLevelSummary> findAllPublishedSummaries(Pageable pageable);

    @Query("SELECT l FROM AbLevel l WHERE l.idCreator = :userId AND l.stDeleted = false ORDER BY l.dtCreated DESC")
    List<AbLevel> findByCreator(@Param("userId") UUID userId);

    @Query("SELECT COUNT(s) > 0 FROM AbScore s WHERE s.idLevel = :levelId AND s.idUser = :userId AND s.nrScore >= :par")
    boolean creatorHasBeatenLevel(@Param("levelId") UUID levelId, @Param("userId") UUID userId, @Param("par") int par);
}