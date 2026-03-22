package com.main.accord.domain.dm;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "DM_CONVERSATION")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Conversation {

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_CONVERSATION")
    private UUID idConversation;

    @Column(name = "DS_NAME", length = 100)
    private String dsName;

    @Column(name = "DS_ICON_URL")
    private String dsIconUrl;

    @Builder.Default @Column(name = "ST_GROUP") private Boolean stGroup = false;

    @Column(name = "ID_OWNER")
    private UUID idOwner;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;
}
