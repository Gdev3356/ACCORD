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
    @Query("SELECT l FROM AbLevel l WHERE l.stPublished = true AND l.stDeleted = false ORDER BY l.dtCreated DESC")
    List<AbLevel> findAllPublished(Pageable pageable);

    @Query("SELECT l FROM AbLevel l WHERE l.idCreator = :userId AND l.stDeleted = false ORDER BY l.dtCreated DESC")
    List<AbLevel> findByCreator(@Param("userId") UUID userId);

    @Query("SELECT COUNT(s) > 0 FROM AbScore s WHERE s.idLevel = :levelId AND s.idUser = :userId AND s.nrScore >= :par")
    boolean creatorHasBeatenLevel(
            @Param("levelId") UUID levelId,
            @Param("userId")  UUID userId,
            @Param("par")     int  par
    );

    @Query(value = """
    SELECT l.id_level, l.id_creator, l.ds_name, l.ds_desc,
           l.nr_par_score, l.st_published, l.st_verified, l.dt_created,
           COALESCE(SUM(CASE WHEN v.st_upvote = true  THEN 1 ELSE 0 END), 0) AS nr_upvotes,
           COALESCE(SUM(CASE WHEN v.st_upvote = false THEN 1 ELSE 0 END), 0) AS nr_downvotes,
           COUNT(DISTINCT s.id_user)    AS nr_plays,
           COUNT(DISTINCT c.id_comment) AS nr_comments
    FROM ab_level l
    LEFT JOIN ab_level_vote    v ON v.id_level = l.id_level
    LEFT JOIN ab_score         s ON s.id_level = l.id_level
    LEFT JOIN ab_level_comment c ON c.id_level = l.id_level AND c.st_deleted = false
    WHERE l.st_published = true AND l.st_deleted = false
    GROUP BY l.id_level, l.dt_created
    ORDER BY l.dt_created DESC
    """,
            countQuery = """
    SELECT COUNT(*) FROM ab_level
    WHERE st_published = true AND st_deleted = false
    """,
            nativeQuery = true)
    List<AbLevelSummary> findAllPublishedSummaries(Pageable pageable);
}