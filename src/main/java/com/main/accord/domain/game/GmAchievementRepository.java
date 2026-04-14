package com.main.accord.domain.game;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GmAchievementRepository extends JpaRepository<GmAchievement, UUID> {
    Optional<GmAchievement> findByDsKey(String key);
}