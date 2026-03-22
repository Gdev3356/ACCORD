package com.main.accord.domain.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface BanLogRepository extends JpaRepository<BanLog, UUID> {

    @Query("""
        SELECT COUNT(b) > 0 FROM BanLog b
        WHERE b.idUser   = :userId
          AND b.stLifted = false
          AND (b.dtExpires IS NULL OR b.dtExpires > CURRENT_TIMESTAMP)
    """)
    boolean isCurrentlyBanned(UUID userId);

    @Query("""
        SELECT b FROM BanLog b WHERE b.idUser = :userId
        ORDER BY b.dtBanned DESC
    """)
    List<BanLog> findAllByUser(UUID userId);
}