package com.main.accord.domain.account;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "AC_REFRESH_TOKEN")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_TOKEN")
    private UUID idToken;

    @Column(name = "ID_USER", nullable = false)
    private UUID idUser;

    @Column(name = "DS_TOKEN", nullable = false, unique = true)
    private String dsToken;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;

    @Column(name = "DT_EXPIRES", nullable = false)
    private OffsetDateTime dtExpires;

    @Builder.Default
    @Column(name = "ST_REVOKED")
    private Boolean stRevoked = false;
}