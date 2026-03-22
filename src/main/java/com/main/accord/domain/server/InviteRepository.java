package com.main.accord.domain.server;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InviteRepository extends JpaRepository<Invite, UUID> {

    Optional<Invite> findByDsCode(String code);

    // All active invites for a server (for the invite management UI)
    @Query("""
        SELECT i FROM Invite i
        WHERE i.idServer = :serverId
          AND i.stStatus = com.main.accord.domain.server.InviteStatus.active
        ORDER BY i.dtCreated DESC
    """)
    List<Invite> findActiveByServer(UUID serverId);

    // Expire invites that have hit their max uses or passed their expiry date
    @Modifying
    @Query("""
        UPDATE Invite i SET i.stStatus = com.main.accord.domain.server.InviteStatus.expired
        WHERE i.stStatus = com.main.accord.domain.server.InviteStatus.active
          AND (
            (i.dtExpires IS NOT NULL AND i.dtExpires < CURRENT_TIMESTAMP)
            OR
            (i.nrMaxUses IS NOT NULL AND i.nrUses >= i.nrMaxUses)
          )
    """)
    void expireStale();
}