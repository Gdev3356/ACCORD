package com.main.accord.domain.webhook;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
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

    @Column(name = "TP_EVENT", nullable = false, length = 50)
    private String tpEvent; // "MEMBER_JOIN", "DM_START"

    @Column(name = "DS_MESSAGE_TEMPLATE", columnDefinition = "TEXT")
    private String dsMessageTemplate; // e.g., "Welcome {user} to {server}!"

    @Builder.Default
    @Column(name = "ST_ACTIVE")
    private Boolean stActive = true;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;
}