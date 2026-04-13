package com.main.accord.domain.game;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "GM_GAME")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GmGame {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_GAME")
    private UUID idGame;

    @Column(name = "DS_SLUG", nullable = false, unique = true, length = 50)
    private String dsSlug;

    @Column(name = "DS_NAME", nullable = false, length = 100)
    private String dsName;

    @Column(name = "DS_DESC")
    private String dsDesc;

    @Builder.Default
    @Column(name = "ST_ACTIVE")
    private Boolean stActive = true;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;
}