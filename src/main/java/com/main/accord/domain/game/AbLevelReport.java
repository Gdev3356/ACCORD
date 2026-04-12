package com.main.accord.domain.game;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "AB_LEVEL_REPORT")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AbLevelReport {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_REPORT") private UUID idReport;

    @Column(name = "ID_LEVEL",    nullable = false) private UUID idLevel;
    @Column(name = "ID_REPORTER", nullable = false) private UUID idReporter;

    @Column(name = "DS_REASON", columnDefinition = "TEXT")
    private String dsReason;

    @Builder.Default
    @Column(name = "ST_STATUS") private String stStatus = "pending";

    @Column(name = "ID_REVIEWED_BY") private UUID idReviewedBy;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false) private OffsetDateTime dtCreated;
    @Column(name = "DT_REVIEWED")                   private OffsetDateTime dtReviewed;
}