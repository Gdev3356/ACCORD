package com.main.accord.domain.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByDsHandleIgnoreCase(String handle);

    boolean existsByDsHandle(String handle);

    @Query("""
        SELECT COUNT(b) > 0 FROM BanLog b
        WHERE b.idUser   = :userId
          AND b.stLifted = false
          AND (b.dtExpires IS NULL OR b.dtExpires > CURRENT_TIMESTAMP)
    """)
    boolean isCurrentlyBanned(UUID userId);

    @Query("""
        SELECT a.idUser         AS idUser,
               a.dsDisplayName  AS dsDisplayName,
               a.dsHandle       AS dsHandle,
               v.dsPfpUrl       AS dsPfpUrl
        FROM Account a
        LEFT JOIN AC_VISUALS v ON v.idUser = a.idUser
        WHERE a.idUser = :id
    """)
        Optional<AccountSummary> findSummaryById(UUID id);

    interface AccountSummary {
        UUID getIdUser();
        String getDsDisplayName();
        String getDsHandle();
        String getDsPfpUrl();
    }
}