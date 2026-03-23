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

    @Query(value = """
    SELECT a.ID_USER        AS idUser,
           a.DS_DISPLAY_NAME AS dsDisplayName,
           a.DS_HANDLE      AS dsHandle,
           v.DS_PFP_URL     AS dsPfpUrl
    FROM AC_ACCOUNT a
    LEFT JOIN AC_VISUALS v ON v.ID_USER = a.ID_USER
    WHERE a.ID_USER = :id
""", nativeQuery = true)
    Optional<AccountSummary> findSummaryById(UUID id);

    interface AccountSummary {
        UUID getIdUser();
        String getDsDisplayName();
        String getDsHandle();
        String getDsPfpUrl();
    }
}