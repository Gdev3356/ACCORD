package com.main.accord.domain.notification;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "NT_NOTIFICATION")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_NOTIF")
    private UUID idNotif;

    @Column(name = "ID_USER", nullable = false)
    private UUID idUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "TP_NOTIF", nullable = false)
    private NotifType tpNotif;

    @Column(name = "DS_TITLE", length = 150)
    private String dsTitle;

    @Column(name = "DS_BODY", columnDefinition = "TEXT")
    private String dsBody;

    @Type(JsonBinaryType.class)
    @Column(name = "JS_PAYLOAD", columnDefinition = "jsonb")
    private Map<String, Object> jsPayload;

    @Column(name = "ST_READ")
    private Boolean stRead = false;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;

    @Column(name = "DT_READ")
    private OffsetDateTime dtRead;
}