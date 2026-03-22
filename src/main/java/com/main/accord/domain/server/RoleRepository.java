package com.main.accord.domain.server;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    // All roles for a server, highest position first (most powerful on top)
    List<Role> findByIdServerOrderByNrPositionDesc(UUID serverId);

    Optional<Role> findByIdRoleAndIdServer(UUID roleId, UUID serverId);

    boolean existsByIdServerAndDsName(UUID serverId, String dsName);

    @Modifying
    @Query("DELETE FROM Role r WHERE r.idRole = :roleId AND r.idServer = :serverId")
    void deleteByIdRoleAndIdServer(UUID roleId, UUID serverId);
}