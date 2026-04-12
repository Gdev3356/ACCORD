package com.main.accord.domain.game;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface GmPlayerAchievementRepository
        extends JpaRepository<GmPlayerAchievement, GmPlayerAchievement.PlayerAchId> {

    @Query("SELECT pa FROM GmPlayerAchievement pa WHERE pa.idUser = :userId")
    List<GmPlayerAchievement> findByUser(@Param("userId") UUID userId);

    boolean existsByIdUserAndIdAchievement(UUID userId, UUID achievementId);
}