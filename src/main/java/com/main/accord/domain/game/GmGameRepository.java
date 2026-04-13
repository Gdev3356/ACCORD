package com.main.accord.domain.game;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface GmGameRepository extends JpaRepository<GmGame, UUID> {
    Optional<GmGame> findByDsSlug(String dsSlug);
}