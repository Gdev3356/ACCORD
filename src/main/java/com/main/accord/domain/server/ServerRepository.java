package com.main.accord.domain.server;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServerRepository extends JpaRepository<Server, UUID> {

    // All servers a user is a member of
    @Query("""
        SELECT s FROM Server s
        JOIN Member m ON m.idServer = s.idServer
        WHERE m.idUser = :userId
        ORDER BY s.dtCreated ASC
    """)
    List<Server> findByMember(UUID userId);

    // Public servers for discovery
    List<Server> findByStPublicTrueOrderByDsNameAsc();

    boolean existsByIdOwner(UUID ownerId);

    @Query("""
        SELECT s.idServer AS idServer, s.dsName AS dsName, s.dsIconUrl AS dsIconUrl
        FROM Server s
        WHERE s.idServer = :id
    """)
    Optional<ServerSummary> findSummaryById(UUID id);

    // projection interface (can be a nested interface or a top-level one)
    interface ServerSummary {
        UUID getIdServer();
        String getDsName();
        String getDsIconUrl();
    }
}
