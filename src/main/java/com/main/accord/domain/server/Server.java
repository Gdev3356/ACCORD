package com.main.accord.domain.server;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "SV_SERVER")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Server {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_SERVER")
    private UUID idServer;

    @Column(name = "ID_OWNER", nullable = false)
    private UUID idOwner;

    @Column(name = "DS_NAME", nullable = false, length = 100)
    private String dsName;

    @Column(name = "DS_DESCRIPTION", columnDefinition = "TEXT")
    private String dsDescription;

    @Column(name = "DS_ICON_URL")
    private String dsIconUrl;

    @Column(name = "DS_BANNER_URL")
    private String dsBannerUrl;

    @Builder.Default @Column(name = "NR_PERMISSIONS") private Long    nrPermissions = 0L;

    @Builder.Default @Column(name = "ST_PUBLIC")      private Boolean stPublic      = false;

    @Builder.Default @Column(name = "ST_VERIFIED")    private Boolean stVerified    = false;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;
}