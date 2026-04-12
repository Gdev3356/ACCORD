package com.main.accord.domain.game;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "GM_PLAYER_ACHIEVEMENT")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(GmPlayerAchievement.PlayerAchId.class)
public class GmPlayerAchievement {

    @Id @Column(name = "ID_USER")        private UUID idUser;
    @Id @Column(name = "ID_ACHIEVEMENT") private UUID idAchievement;

    @Builder.Default
    @Column(name = "DT_UNLOCKED") private OffsetDateTime dtUnlocked = OffsetDateTime.now();

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class PlayerAchId implements Serializable {
        private UUID idUser;
        private UUID idAchievement;
    }
}