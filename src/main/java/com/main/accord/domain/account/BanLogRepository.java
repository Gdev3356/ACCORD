package com.main.accord.domain.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BanLogRepository extends JpaRepository<BanLog, UUID> {

    @Query("""
        SELECT b FROM BanLog b WHERE b.idUser = :userId
        ORDER BY b.dtBanned DESC
    """)
    List<BanLog> findAllByUser(UUID userId);
}