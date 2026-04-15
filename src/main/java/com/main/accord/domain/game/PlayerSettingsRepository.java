package com.main.accord.domain.game;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerSettingsRepository
        extends JpaRepository<PlayerSettings, PlayerSettingsId> {

    Optional<PlayerSettings> findByIdUserAndIdGame(UUID idUser, UUID idGame);
}