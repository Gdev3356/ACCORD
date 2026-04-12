package com.main.accord.domain.game;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity @Table(name = "AB_LEVEL_COMMENT")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AbLevelComment {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_COMMENT") private UUID idComment;

    @Column(name = "ID_LEVEL", nullable = false) private UUID idLevel;
    @Column(name = "ID_USER",  nullable = false) private UUID idUser;

    @Column(name = "DS_CONTENT", columnDefinition = "TEXT", nullable = false)
    private String dsContent;

    @Builder.Default @Column(name = "ST_DELETED") private Boolean stDeleted = false;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false) private OffsetDateTime dtCreated;
}