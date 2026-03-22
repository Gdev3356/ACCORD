package com.main.accord.domain.dm;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "DM_MESSAGE")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DmMessage {

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_MESSAGE")
    private UUID idMessage;

    @Column(name = "ID_CONVERSATION", nullable = false)
    private UUID idConversation;

    @Column(name = "ID_AUTHOR")
    private UUID idAuthor;

    @Column(name = "ID_REPLY_TO")
    private UUID idReplyTo;

    @Column(name = "DS_CONTENT", columnDefinition = "TEXT")
    private String dsContent;

    @Column(name = "ST_EDITED")  private Boolean stEdited  = false;
    @Column(name = "ST_DELETED") private Boolean stDeleted = false;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;

    @Column(name = "DT_EDITED")
    private OffsetDateTime dtEdited;
}