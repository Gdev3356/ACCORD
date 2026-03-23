package com.main.accord.domain.account;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "AC_ACCOUNT")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Account {

    @Id
    @Column(name = "ID_USER")
    private UUID idUser;

    @Column(name = "DS_HANDLE", nullable = false, unique = true, length = 30)
    private String dsHandle;

    @Column(name = "DS_DISPLAY_NAME", nullable = false, length = 50)
    private String dsDisplayName;

    @Column(name = "DS_PRONOUNS", length = 30)
    private String dsPronouns;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;

    @Column(name = "DT_LAST_LOGIN")
    private OffsetDateTime dtLastLogin;

    @Builder.Default
    @Column(name = "ST_ACTIVE")
    private Boolean stActive = true;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "ST_PRESENCE", columnDefinition = "presence_status")
    private PresenceStatus stPresence = PresenceStatus.online;

    @Builder.Default
    @Column(name = "ST_NOTIFICATIONS_ENABLED")
    private Boolean stNotificationsEnabled = true;
}