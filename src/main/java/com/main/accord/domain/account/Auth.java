package com.main.accord.domain.account;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "AC_AUTH")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Auth {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_USER")
    private UUID idUser;

    @Column(name = "DS_EMAIL", nullable = false, unique = true, length = 100)
    private String dsEmail;

    @Column(name = "DS_PASSWORD", nullable = false)
    private String dsPassword;  // BCrypt hash — never expose this

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;

    @Builder.Default
    @Column(name = "ST_ACTIVE")
    private Boolean stActive = true;

    @Builder.Default
    @Column(name = "ST_ADMIN")
    private Boolean stAdmin = false;
}