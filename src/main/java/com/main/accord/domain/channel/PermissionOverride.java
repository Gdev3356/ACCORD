package com.main.accord.domain.channel;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "CH_PERMISSION_OVERRIDE")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(PermissionOverride.PermissionOverrideId.class)
public class PermissionOverride {

    @Id
    @Column(name = "ID_CHANNEL", nullable = false)
    private UUID idChannel;

    @Id
    @Column(name = "ID_ROLE")
    private UUID idRole;

    @Id
    @Column(name = "ID_USER")
    private UUID idUser;

    @Builder.Default @Column(name = "NR_ALLOW")
    private Long nrAllow = 0L;

    @Builder.Default @Column(name = "NR_DENY")
    private Long nrDeny  = 0L;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class PermissionOverrideId implements Serializable {
        private UUID idChannel;
        private UUID idRole;
        private UUID idUser;
    }
}