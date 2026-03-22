package com.main.accord.domain.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface EmailVerifyTokenRepository extends JpaRepository<EmailVerifyToken, UUID> {
    Optional<EmailVerifyToken> findByDsToken(String token);

    @Modifying
    @Query("DELETE FROM EmailVerifyToken t WHERE t.idUser = :userId")
    void deleteAllForUser(UUID userId);
}