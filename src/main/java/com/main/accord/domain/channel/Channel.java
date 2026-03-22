package com.main.accord.domain.channel;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "CH_CHANNEL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_CHANNEL")
    private UUID idChannel;

    @Column(name = "ID_SERVER")
    private UUID idServer;   // null for DM channels

    @Column(name = "ID_PARENT")
    private UUID idParent;   // category parent

    @Column(name = "DS_NAME", nullable = false, length = 100)
    private String dsName;

    @Column(name = "DS_TOPIC", length = 1024)
    private String dsTopic;

    @Enumerated(EnumType.STRING)
    @Column(name = "TP_CHANNEL", nullable = false)
    private ChannelType tpChannel = ChannelType.text;

    @Column(name = "NR_POSITION")
    private Short nrPosition = 0;

    @Column(name = "NR_SLOWMODE")
    private Integer nrSlowmode = 0;

    @Column(name = "ST_NSFW")
    private Boolean stNsfw = false;

    @Column(name = "ST_ARCHIVED")
    private Boolean stArchived = false;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;
}