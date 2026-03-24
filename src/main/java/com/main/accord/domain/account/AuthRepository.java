package com.main.accord.domain.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthRepository extends JpaRepository<Auth, UUID> {
    Optional<Auth> findByDsEmail(String email);
    boolean existsByDsEmail(String email);

    @Modifying
    @Query("UPDATE Auth a SET a.dsPassword = :hash WHERE a.idUser = :id")
    void updatePassword(@Param("id") UUID id, @Param("hash") String hash);

    @Query("SELECT a.idUser FROM Auth a WHERE a.stAdmin = true AND a.stActive = true")
    List<UUID> findAllAdmins();
}