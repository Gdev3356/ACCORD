package com.main.accord.domain.account;

import com.main.accord.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VisualsService {

    private final VisualsRepository visualsRepository;

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