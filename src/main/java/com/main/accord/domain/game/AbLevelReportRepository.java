package com.main.accord.domain.game;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface AbLevelReportRepository extends JpaRepository<AbLevelReport, UUID> {

    @Query("SELECT r FROM AbLevelReport r WHERE r.stStatus = 'pending' ORDER BY r.dtCreated DESC")
    List<AbLevelReport> findPending();

    boolean existsByIdLevelAndIdReporter(UUID levelId, UUID reporterId);
}