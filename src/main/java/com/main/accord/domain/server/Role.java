package com.main.accord.domain.server;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "SV_ROLE")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_ROLE")
    private UUID idRole;

    @Column(name = "ID_SERVER", nullable = false)
    private UUID idServer;

    @Column(name = "DS_NAME", nullable = false, length = 50)
    private String dsName;

    @Column(name = "NR_COLOR")
    private Integer nrColor;

    @Column(name = "NR_PERMISSIONS")
    private Long nrPermissions = 0L;

    @Column(name = "NR_POSITION")
    private Short nrPosition = 0;

    @Column(name = "ST_MENTIONABLE")
    private Boolean stMentionable = false;

    @Column(name = "ST_HOISTED")
    private Boolean stHoisted = false;
}