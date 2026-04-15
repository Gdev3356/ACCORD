package com.main.accord.domain.game;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "GM_PLAYER_SETTINGS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(PlayerSettingsId.class)
public class PlayerSettings {

    @Id
    @Column(name = "ID_USER", nullable = false)
    private UUID idUser;

    @Id
    @Column(name = "ID_GAME", nullable = false)
    private UUID idGame;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "JS_SETTINGS", columnDefinition = "jsonb")
    private Map<String, Object> jsSettings = new HashMap<>();

    @Column(name = "DT_UPDATED")
    private OffsetDateTime dtUpdated;
}