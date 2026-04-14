package com.main.accord.domain.channel;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "CH_READ_STATE")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(ChReadStateId.class)
public class ChReadState {

    @Id @Column(name = "ID_CHANNEL", nullable = false)
    private UUID idChannel;

    @Id @Column(name = "ID_USER", nullable = false)
    private UUID idUser;

    @Column(name = "ID_LAST_READ_MSG")
    private UUID idLastReadMsg;

    @Builder.Default
    @Column(name = "DT_LAST_READ")
    private OffsetDateTime dtLastRead = OffsetDateTime.now();

    @Builder.Default
    @Column(name = "ST_MUTED")
    private Boolean stMuted = false;
}