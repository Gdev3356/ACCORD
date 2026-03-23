package com.main.accord.domain.server;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "SV_EMOJI")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SvEmoji {

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_EMOJI")
    private UUID idEmoji;

    @Column(name = "ID_SERVER", nullable = false)
    private UUID idServer;

    @Column(name = "ID_CREATOR")
    private UUID idCreator;

    @Column(name = "DS_NAME", length = 50, nullable = false)
    private String dsName;

    @Column(name = "DS_URL", nullable = false)
    private String dsUrl;

    @Builder.Default
    @Column(name = "ST_ANIMATED")
    private Boolean stAnimated = false;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;
}