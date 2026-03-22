package com.main.accord.domain.server;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "SV_MEMBER")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(Member.MemberId.class)
public class Member {

    @Id @Column(name = "ID_SERVER") private UUID idServer;
    @Id @Column(name = "ID_USER")   private UUID idUser;

    @Column(name = "DS_NICKNAME", length = 50)
    private String dsNickname;

    @CreationTimestamp
    @Column(name = "DT_JOINED", updatable = false)
    private OffsetDateTime dtJoined;

    @Column(name = "ST_MUTED")   private Boolean stMuted    = false;
    @Column(name = "ST_DEAFENED") private Boolean stDeafened = false;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class MemberId implements Serializable {
        private UUID idServer;
        private UUID idUser;
    }
}