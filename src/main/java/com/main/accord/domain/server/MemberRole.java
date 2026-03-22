package com.main.accord.domain.server;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "SV_MEMBER_ROLE")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(MemberRole.MemberRoleId.class)
public class MemberRole {

    @Id @Column(name = "ID_SERVER") private UUID idServer;
    @Id @Column(name = "ID_USER")   private UUID idUser;
    @Id @Column(name = "ID_ROLE")   private UUID idRole;

    @CreationTimestamp
    @Column(name = "DT_GRANTED", updatable = false)
    private OffsetDateTime dtGranted;

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class MemberRoleId implements Serializable {
        private UUID idServer;
        private UUID idUser;
        private UUID idRole;
    }
}