package com.main.accord.domain.game;

import java.time.OffsetDateTime;
import java.util.UUID;

public record UnlockedAchievement(
        UUID idAchievement,
        String          dsKey,
        String          dsTitle,
        String          dsDesc,
        String          dsIconUrl,
        boolean         stSecret,
        OffsetDateTime dtUnlocked
) {}