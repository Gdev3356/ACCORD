package com.main.accord.domain.dm;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity(name = "DmParticipant")
@Table(name = "DM_PARTICIPANT")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(Participant.ParticipantId.class)
public class Participant {

    @Id @Column(name = "ID_CONVERSATION") private UUID idConversation;
    @Id @Column(name = "ID_USER")         private UUID idUser;

    @CreationTimestamp
    @Column(name = "DT_JOINED", updatable = false)
    private OffsetDateTime dtJoined;

    @Column(name = "DT_LEFT")
    private OffsetDateTime dtLeft;   // NULL = still active

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
    public static class ParticipantId implements Serializable {
        private UUID idConversation;
        private UUID idUser;
    }
}