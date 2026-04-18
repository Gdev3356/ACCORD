package com.main.accord.domain.notification;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "NT_NOTIFICATION")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ID_NOTIF")
    private UUID idNotif;

    @Column(name = "ID_USER", nullable = false)
    private UUID idUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "TP_NOTIF", nullable = false)
    @org.hibernate.annotations.ColumnTransformer(write = "?::notif_type")
    private NotifType tpNotif;

    @Column(name = "DS_TITLE", length = 150)
    private String dsTitle;

    @Column(name = "DS_BODY", columnDefinition = "TEXT")
    private String dsBody;

    @Column(name = "js_payload") // now a plain text/varchar column
    private String jsPayload;    // stores encrypted JSON string

    @Transient
    private Map<String, Object> jsPayloadDecrypted; // used only in-memory / for WebSocket push

    @Builder.Default @Column(name = "ST_READ") private Boolean stRead = false;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;

    @Column(name = "DT_READ")
    private OffsetDateTime dtRead;
}