package com.main.accord.domain.game;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "AB_SCORE",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ID_LEVEL","ID_USER"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AbScore {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_SCORE") private UUID idScore;

    @Column(name = "ID_LEVEL", nullable = false) private UUID idLevel;
    @Column(name = "ID_USER",  nullable = false) private UUID idUser;
    @Column(name = "NR_SCORE", nullable = false) private Integer nrScore;

    @Builder.Default
    @Column(name = "NR_STARS", nullable = false) private Short nrStars = 1;

    @CreationTimestamp
    @Column(name = "DT_ACHIEVED") private OffsetDateTime dtAchieved;
}