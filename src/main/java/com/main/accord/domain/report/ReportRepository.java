package com.main.accord.domain.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    @Query("SELECT r FROM Report r WHERE r.stStatus = 'pending' ORDER BY r.dtCreated DESC")
    List<Report> findAllPending();

    @Query("SELECT r FROM Report r ORDER BY r.dtCreated DESC")
    List<Report> findAllOrdered();
}