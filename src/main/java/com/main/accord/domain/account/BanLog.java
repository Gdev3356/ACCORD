package com.main.accord.domain.account;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "AC_BAN_LOG")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BanLog {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_BAN")
    private UUID idBan;

    @Column(name = "ID_USER", nullable = false)
    private UUID idUser;

    @Column(name = "ID_BANNED_BY")
    private UUID idBannedBy;

    @Column(name = "DS_REASON", columnDefinition = "TEXT")
    private String dsReason;

    @CreationTimestamp
    @Column(name = "DT_BANNED")
    private OffsetDateTime dtBanned;

    @Column(name = "DT_EXPIRES")
    private OffsetDateTime dtExpires;

    @Builder.Default @Column(name = "ST_LIFTED") private Boolean stLifted = false;

    @Column(name = "DT_LIFTED_AT")
    private OffsetDateTime dtLiftedAt;

    @Column(name = "ID_LIFTED_BY")
    private UUID idLiftedBy;
}