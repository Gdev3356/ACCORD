package com.main.accord.domain.account;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "AC_VISUALS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Visuals {

    @Id
    @Column(name = "ID_USER")
    private UUID idUser;

    @Column(name = "DS_PFP_URL")
    private String dsPfpUrl;

    @Column(name = "DS_BANNER_URL")
    private String dsBannerUrl;

    @Column(name = "DS_BIO", columnDefinition = "TEXT")
    private String dsBio;

    @Column(name = "NR_BG_COLOR")
    private Integer nrBgColor;

    @Builder.Default @Column(name = "ST_MODE", length = 1)
    private String stMode = "D";

    @Column(name = "ID_DECORATION")
    private UUID idDecoration;

    @Column(name = "ID_EFFECT")
    private UUID idEffect;

    @UpdateTimestamp
    @Column(name = "DT_UPDATED_AT")
    private OffsetDateTime dtUpdatedAt;
}