package com.main.accord.domain.dm;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "DM_MESSAGE")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DmMessage {

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_MESSAGE")
    private UUID idMessage;

    @OneToMany(fetch = FetchType.EAGER)
    @JoinColumn(name = "ID_MESSAGE", referencedColumnName = "ID_MESSAGE", insertable = false, updatable = false)
    private List<DmAttachment> attachments = new java.util.ArrayList<>();

    @Column(name = "ID_CONVERSATION", nullable = false)
    private UUID idConversation;

    @Column(name = "ID_AUTHOR")
    private UUID idAuthor;

    @Column(name = "ID_REPLY_TO")
    private UUID idReplyTo;

    @Column(name = "DS_CONTENT", columnDefinition = "TEXT")
    private String dsContent;

    @Builder.Default @Column(name = "ST_EDITED")  private Boolean stEdited  = false;

    @Builder.Default @Column(name = "ST_DELETED") private Boolean stDeleted = false;

    @CreationTimestamp
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", timezone = "UTC")
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;

    @Column(name = "DT_EDITED")
    private OffsetDateTime dtEdited;
}