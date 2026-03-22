package com.main.accord.domain.channel;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "CH_PERMISSION_OVERRIDE")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PermissionOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;  // surrogate PK — easier than the 3-col composite in JPA

    @Column(name = "ID_CHANNEL", nullable = false)
    private UUID idChannel;

    @Column(name = "ID_ROLE")
    private UUID idRole;

    @Column(name = "ID_USER")
    private UUID idUser;

    @Column(name = "NR_ALLOW")
    private Long nrAllow = 0L;

    @Column(name = "NR_DENY")
    private Long nrDeny  = 0L;
}