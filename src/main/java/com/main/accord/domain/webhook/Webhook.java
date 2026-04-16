package com.main.accord.domain.webhook;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "SV_WEBHOOK")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Webhook {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_WEBHOOK")
    private UUID idWebhook;

    @Column(name = "ID_SERVER", nullable = false)
    private UUID idServer;

    @Column(name = "ID_CHANNEL", nullable = false)
    private UUID idChannel;

    @Column(name = "DS_NAME", nullable = false, length = 100)
    private String dsName;

    @Column(name = "DS_AVATAR_URL")
    private String dsAvatarUrl;

    @Column(name = "DS_BIO")
    private String dsBio;

    @Column(name = "DS_BANNER_URL")
    private String dsBannerUrl;

    @Column(name = "NR_COLOR")
    private Integer nrColor;

    // Store as JSON array of events
    @Column(name = "JS_EVENTS", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private List<String> jsEvents;  // ["MEMBER_JOIN", "MEMBER_LEAVE"]

    // Store templates as JSON object
    @Column(name = "JS_TEMPLATES", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, String> jsTemplates;  // {"MEMBER_JOIN": "Welcome {user}!", "MEMBER_LEAVE": "Goodbye {user}!"}

    @Builder.Default
    @Column(name = "ST_ACTIVE")
    private Boolean stActive = true;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;
}