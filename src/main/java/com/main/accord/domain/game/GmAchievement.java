package com.main.accord.domain.game;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "GM_ACHIEVEMENT")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GmAchievement {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_ACHIEVEMENT") private UUID idAchievement;

    /** null = platform-wide achievement */
    @Column(name = "ID_GAME") private UUID idGame;

    @Column(name = "DS_KEY",   nullable = false, unique = true, length = 80) private String dsKey;
    @Column(name = "DS_TITLE", nullable = false, length = 100)               private String dsTitle;
    @Column(name = "DS_DESC",  columnDefinition = "TEXT")                    private String dsDesc;
    @Column(name = "DS_ICON_URL")                                            private String dsIconUrl;

    @Builder.Default @Column(name = "ST_SECRET") private Boolean stSecret = false;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false) private OffsetDateTime dtCreated;
}