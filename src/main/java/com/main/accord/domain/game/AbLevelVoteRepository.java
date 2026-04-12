package com.main.accord.domain.game;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AbLevelVoteRepository extends JpaRepository<AbLevelVote, AbLevelVote.VoteId> {
    Optional<AbLevelVote> findByIdLevelAndIdUser(UUID levelId, UUID userId);
}