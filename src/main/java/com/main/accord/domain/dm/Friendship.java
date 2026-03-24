package com.main.accord.domain.dm;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "DM_FRIENDSHIP")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@IdClass(Friendship.FriendshipId.class)
public class Friendship {

    @Id @Column(name = "ID_USER_A") private UUID idUserA;
    @Id @Column(name = "ID_USER_B") private UUID idUserB;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "ST_STATUS", nullable = false)
    @org.hibernate.annotations.ColumnTransformer(write = "?::friend_status")
    private FriendStatus stStatus = FriendStatus.pending;

    @Column(name = "ID_REQUESTER", nullable = false)
    private UUID idRequester;

    @CreationTimestamp
    @Column(name = "DT_CREATED", updatable = false)
    private OffsetDateTime dtCreated;

    @Column(name = "DT_UPDATED")
    private OffsetDateTime dtUpdated;

    /** Canonical ordering: always store smaller UUID as ID_USER_A */
    public static Friendship create(UUID a, UUID b, UUID requester) {
        UUID lo = a.compareTo(b) < 0 ? a : b;
        UUID hi = a.compareTo(b) < 0 ? b : a;
        return Friendship.builder()
                .idUserA(lo).idUserB(hi)
                .idRequester(requester)
                .stStatus(FriendStatus.pending)
                .build();
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class FriendshipId implements Serializable {
        private UUID idUserA;
        private UUID idUserB;
    }
}