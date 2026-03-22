package com.main.accord.domain.server;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "SV_INVITE")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Invite {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_INVITE")
    private UUID idInvite;

    @Column(name = "DS_CODE", nullable = false, unique = true, length = 12)
    private String dsCode;

    @Column(name = "ID_SERVER", nullable = false)
    private UUID idServer;

    @Column(name = "ID_CHANNEL")
    private UUID idChannel;

    @Column(name = "ID_CREATOR")
    private UUID idCreator;

    @Column(name = "NR_MAX_USES")
    private Integer nrMaxUses;          // NULL = unlimited

    @Column(name = "NR_USES")
    private Integer nrUses = 0;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;

    @Column(name = "DT_EXPIRES")
    private OffsetDateTime dtExpires;   // NULL = never

    @Enumerated(EnumType.STRING)
    @Column(name = "ST_STATUS", nullable = false)
    private InviteStatus stStatus = InviteStatus.active;
}