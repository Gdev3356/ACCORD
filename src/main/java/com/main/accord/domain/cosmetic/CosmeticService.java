package com.main.accord.domain.cosmetic;

import com.main.accord.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CosmeticService {

    private final CosmeticRepository cosmeticRepository;

    public List<Cosmetic> getAll() {
        return cosmeticRepository.findByStActiveTrue();
    }

    public List<Cosmetic> getByType(Cosmetic.CosmeticType type) {
        return cosmeticRepository.findByTpCosmeticAndStActiveTrue(type);
    }

    @Transactional
    public Cosmetic create(CreateCosmeticRequest req) {
        return cosmeticRepository.save(Cosmetic.builder()
                .dsKey(req.key())
                .dsLabel(req.label())
                .tpCosmetic(req.type())
                .dsAssetUrl(req.assetUrl())
                .build());
    }

    @Transactional
    public Cosmetic setActive(UUID id, boolean active) {
        Cosmetic c = cosmeticRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Cosmetic not found."));
        c.setStActive(active);
        return cosmeticRepository.save(c);
    }

    public record CreateCosmeticRequest(
            String              key,
            String              label,
            Cosmetic.CosmeticType type,
            String              assetUrl
    ) {}
}