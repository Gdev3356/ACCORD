package com.main.accord.domain.cosmetic;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "AC_COSMETICS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Cosmetic {

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_COSMETIC")
    private UUID idCosmetic;

    @Column(name = "DS_KEY", nullable = false, unique = true, length = 60)
    private String dsKey;

    @Column(name = "DS_LABEL", nullable = false, length = 80)
    private String dsLabel;

    @Enumerated(EnumType.STRING)
    @Column(name = "TP_COSMETIC", nullable = false)
    private CosmeticType tpCosmetic;

    @Column(name = "DS_ASSET_URL")
    private String dsAssetUrl;

    @Builder.Default
    @Column(name = "ST_ACTIVE")
    private Boolean stActive = true;

    public enum CosmeticType { decoration, effect, badge }
}