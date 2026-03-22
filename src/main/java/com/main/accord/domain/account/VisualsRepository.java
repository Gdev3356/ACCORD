package com.main.accord.domain.account;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VisualsRepository extends JpaRepository<Visuals, UUID> {

    // ID_USER is the PK so findById() already covers most cases.
    // This exists for explicit readability in AccountService.
    boolean existsByIdUser(UUID userId);
}