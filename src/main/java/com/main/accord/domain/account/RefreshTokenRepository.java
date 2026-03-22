package com.main.accord.domain.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByDsToken(String token);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.stRevoked = true WHERE r.idUser = :userId")
    void revokeAllForUser(UUID userId);
}