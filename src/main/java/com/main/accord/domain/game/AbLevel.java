package com.main.accord.domain.game;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity @Table(name = "AB_LEVEL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AbLevel {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_LEVEL") private UUID idLevel;

    @Column(name = "ID_CREATOR", nullable = false)
    private UUID idCreator;

    @Column(name = "DS_NAME", nullable = false, length = 100)
    private String dsName;

    @Column(name = "DS_DESC", columnDefinition = "TEXT")
    private String dsDesc;

    /** Full LevelData JSON — stored verbatim from the TypeScript serialiser. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "JS_DATA", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> jsData;

    @Builder.Default @Column(name = "NR_PAR_SCORE") private Integer nrParScore = 0;
    @Builder.Default @Column(name = "ST_PUBLISHED")  private Boolean stPublished = false;
    @Builder.Default @Column(name = "ST_VERIFIED")   private Boolean stVerified  = false;
    @Builder.Default @Column(name = "ST_DELETED")    private Boolean stDeleted   = false;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false) private OffsetDateTime dtCreated;
    @UpdateTimestamp
    @Column(name = "DT_UPDATED")                    private OffsetDateTime dtUpdated;
}