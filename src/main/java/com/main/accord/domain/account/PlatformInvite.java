package com.main.accord.domain.account;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "AC_PLATFORM_INVITE")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlatformInvite {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_INVITE")
    private UUID idInvite;

    @Column(name = "DS_CODE", nullable = false, unique = true, length = 16)
    private String dsCode;

    @Column(name = "ID_CREATOR", nullable = false)
    private UUID idCreator;

    @Column(name = "ID_USED_BY")
    private UUID idUsedBy;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;

    @Column(name = "DT_EXPIRES")
    private OffsetDateTime dtExpires;

    @Column(name = "DT_USED")
    private OffsetDateTime dtUsed;

    @Builder.Default
    @Column(name = "ST_USED")
    private Boolean stUsed = false;
}