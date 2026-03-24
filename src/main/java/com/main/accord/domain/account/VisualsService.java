package com.main.accord.domain.account;

import com.main.accord.common.NotFoundException;
import com.main.accord.domain.cosmetic.CosmeticRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.main.accord.common.AccordException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VisualsService {

    private final VisualsRepository visualsRepository;
    private final CosmeticRepository cosmeticRepository;

    public Visuals getVisuals(UUID userId) {
        return visualsRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Visuals not found."));
    }

    @Transactional
    public Visuals updateVisuals(UUID userId, UpdateVisualsRequest req) {
        Visuals visuals = visualsRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Visuals not found."));

        if (req.pfpUrl()      != null) visuals.setDsPfpUrl(req.pfpUrl());
        if (req.bannerUrl()   != null) visuals.setDsBannerUrl(req.bannerUrl());
        if (req.bio()         != null) visuals.setDsBio(req.bio());
        if (req.bgColor()     != null) visuals.setNrBgColor(req.bgColor());
        if (req.mode()        != null) visuals.setStMode(req.mode());
        if (req.decoration()  != null) visuals.setIdDecoration(req.decoration());
        if (req.effect()      != null) visuals.setIdEffect(req.effect());
        if (req.decoration() != null && !cosmeticRepository.existsById(req.decoration()))
            throw new AccordException("Decoration not found.");
        if (req.effect() != null && !cosmeticRepository.existsById(req.effect()))
            throw new AccordException("Effect not found.");

        return visualsRepository.save(visuals);
    }

    public record UpdateVisualsRequest(
            String  pfpUrl,
            String  bannerUrl,
            String  bio,
            Integer bgColor,
            String  mode,
            UUID    decoration,
            UUID    effect
    ) {}
}