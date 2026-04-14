package com.main.accord.domain.game;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AbLevelSummary {
    UUID getIdLevel();
    UUID            getIdCreator();
    String          getDsName();
    String          getDsDesc();
    Integer         getNrParScore();
    Boolean         getStPublished();
    Boolean         getStVerified();
    OffsetDateTime getDtCreated();
    Long            getNrUpvotes();
    Long            getNrDownvotes();
    Long            getNrPlays();
    Long            getNrComments();
}