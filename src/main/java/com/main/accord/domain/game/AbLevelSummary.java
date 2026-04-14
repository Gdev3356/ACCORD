package com.main.accord.domain.game;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AbLevelSummary(
        UUID            idLevel,
        UUID            idCreator,
        String          dsName,
        String          dsDesc,
        Integer         nrParScore,
        Boolean         stPublished,
        Boolean         stVerified,
        Boolean         stDeleted,
        OffsetDateTime  dtCreated,
        Long            nrUpvotes,
        Long            nrDownvotes,
        Long            nrPlays,
        Long            nrComments
) {}