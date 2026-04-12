package com.main.accord.domain.game;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "AB_LEVEL_VOTE")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(AbLevelVote.VoteId.class)
public class AbLevelVote {

    @Id @Column(name = "ID_LEVEL") private UUID idLevel;
    @Id @Column(name = "ID_USER")  private UUID idUser;

    @Column(name = "ST_UPVOTE", nullable = false) private Boolean stUpvote;

    @Builder.Default
    @Column(name = "DT_VOTED") private OffsetDateTime dtVoted = OffsetDateTime.now();

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class VoteId implements Serializable {
        private UUID idLevel;
        private UUID idUser;
    }
}