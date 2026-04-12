package com.main.accord.domain.game;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AbScoreRepository extends JpaRepository<AbScore, UUID> {

    Optional<AbScore> findByIdLevelAndIdUser(UUID levelId, UUID userId);

    /** Top-N leaderboard for a level. */
    @Query("SELECT s FROM AbScore s WHERE s.idLevel = :levelId ORDER BY s.nrScore DESC")
    List<AbScore> findLeaderboard(@Param("levelId") UUID levelId,
                                  org.springframework.data.domain.Pageable pageable);
}