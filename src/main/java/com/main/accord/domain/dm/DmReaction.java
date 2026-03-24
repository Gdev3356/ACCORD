package com.main.accord.domain.dm;

import com.main.accord.domain.message.ReactionId;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "DM_REACTION")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ReactionId.class)
public class DmReaction {
    @Id
    @Column(name = "ID_MESSAGE") private UUID idMessage;
    @Id @Column(name = "ID_USER")    private UUID idUser;
    @Id @Column(name = "DS_EMOJI")   private String dsEmoji;

    @CreationTimestamp
    @Column(name = "DT_REACTED")
    private OffsetDateTime dtReacted;
}