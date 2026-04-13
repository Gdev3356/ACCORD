package com.main.accord.domain.game;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class PlayerSettingsId implements Serializable {

    @Column(name = "ID_USER")
    private UUID idUser;

    @Column(name = "ID_GAME")
    private UUID idGame;
}