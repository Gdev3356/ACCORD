package com.main.accord.domain.message;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "MS_REACTION")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(ReactionId.class)
public class Reaction {

    @Id @Column(name = "ID_MESSAGE", nullable = false)
    private UUID idMessage;

    @Id @Column(name = "ID_USER", nullable = false)
    private UUID idUser;

    @Id @Column(name = "DS_EMOJI", nullable = false, length = 50)
    private String dsEmoji;

    @CreationTimestamp
    @Column(name = "DT_REACTED")
    private OffsetDateTime dtReacted;
}