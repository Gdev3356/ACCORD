package com.main.accord.domain.message;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "MS_MESSAGE")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_MESSAGE")
    private UUID idMessage;

    @Column(name = "ID_CHANNEL", nullable = false)
    private UUID idChannel;

    @Column(name = "ID_AUTHOR")
    private UUID idAuthor;

    @Column(name = "ID_REPLY_TO")
    private UUID idReplyTo;

    @Column(name = "DS_CONTENT", columnDefinition = "TEXT")
    private String dsContent;

    @Builder.Default @Column(name = "ST_EDITED")  private Boolean stEdited  = false;
    @Builder.Default @Column(name = "ST_PINNED")  private Boolean stPinned  = false;
    @Builder.Default @Column(name = "ST_DELETED") private Boolean stDeleted = false;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;

    @Column(name = "DT_EDITED")
    private OffsetDateTime dtEdited;

    /**
     * LAZY + @BatchSize replaces the old FetchType.EAGER.
     *
     * EAGER was firing one extra SELECT per message in every paginated load —
     * 50 messages = 50 attachment queries. With LAZY + BatchSize(50), Hibernate
     * fetches attachments for up to 50 messages in a single IN-clause query
     * only when the collection is actually accessed (i.e. during serialisation).
     *
     * If you have a code path that truly needs attachments eagerly, use a
     * JOIN FETCH in that specific repository query instead of pulling EAGER here.
     */
    @OneToMany(fetch = FetchType.LAZY)
    @JoinColumn(name = "ID_MESSAGE", referencedColumnName = "ID_MESSAGE",
            insertable = false, updatable = false)
    @BatchSize(size = 50)
    private List<MsAttachment> attachments = new java.util.ArrayList<>();

    @Builder.Default
    @Column(name = "TP_MESSAGE", nullable = false, length = 20)
    private String tpMessage = "text";

    @Column(name = "JS_ACTIVITY", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private java.util.Map<String, Object> jsActivity;
}