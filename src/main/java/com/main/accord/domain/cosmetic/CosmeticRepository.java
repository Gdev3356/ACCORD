package com.main.accord.domain.cosmetic;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface CosmeticRepository extends JpaRepository<Cosmetic, UUID> {
    List<Cosmetic> findByTpCosmeticAndStActiveTrue(Cosmetic.CosmeticType type);
    List<Cosmetic> findByStActiveTrue();
}