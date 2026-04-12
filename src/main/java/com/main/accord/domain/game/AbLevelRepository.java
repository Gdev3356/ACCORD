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
}