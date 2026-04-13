package com.main.accord.domain.game;

import com.main.accord.domain.account.Account;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "GM_PLAYER_SETTINGS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlayerSettings {

    @EmbeddedId
    private PlayerSettingsId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idUser")
    @JoinColumn(name = "ID_USER")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idGame")
    @JoinColumn(name = "ID_GAME")
    private GmGame game;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "JS_SETTINGS", columnDefinition = "jsonb")
    private Map<String, Object> jsSettings = new HashMap<>();

    @Column(name = "DT_UPDATED")
    private OffsetDateTime dtUpdated;
}