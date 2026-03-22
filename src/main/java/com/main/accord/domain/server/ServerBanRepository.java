package com.main.accord.domain.server;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServerBanRepository extends JpaRepository<ServerBan, ServerBan.ServerBanId> {

    Optional<ServerBan> findByIdServerAndIdUser(UUID serverId, UUID userId);

    List<ServerBan> findByIdServer(UUID serverId);

    boolean existsByIdServerAndIdUser(UUID serverId, UUID userId);
}
