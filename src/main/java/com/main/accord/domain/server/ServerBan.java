package com.main.accord.domain.server;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "SV_BAN")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(ServerBan.ServerBanId.class)
public class ServerBan {

    @Id @Column(name = "ID_SERVER") private UUID idServer;
    @Id @Column(name = "ID_USER")   private UUID idUser;

    @Column(name = "ID_BANNED_BY")
    private UUID idBannedBy;

    @Column(name = "DS_REASON", columnDefinition = "TEXT")
    private String dsReason;

    @CreationTimestamp
    @Column(name = "DT_BANNED", updatable = false)
    private OffsetDateTime dtBanned;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ServerBanId implements Serializable {
        private UUID idServer;
        private UUID idUser;
    }
}