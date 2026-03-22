package com.main.accord.domain.account;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AuthRepository extends JpaRepository<Auth, UUID> {
    Optional<Auth> findByDsEmail(String email);
    boolean existsByDsEmail(String email);
}