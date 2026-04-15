package com.main.accord.domain.server;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MemberRoleRepository extends JpaRepository<MemberRole, MemberRole.MemberRoleId> {

    // Find all member-role associations for a specific user in a server
    List<MemberRole> findByIdServerAndIdUser(UUID serverId, UUID userId);

    // Optional: Find roles by member
    @Query("SELECT mr.idRole FROM MemberRole mr WHERE mr.idServer = :serverId AND mr.idUser = :userId")
    List<UUID> findRoleIdsByMember(UUID serverId, UUID userId);

    @Query("""
        SELECT r FROM Role r
        JOIN MemberRole mr ON mr.idRole = r.idRole
        WHERE mr.idUser = :userId AND mr.idServer = :serverId
    """)
    List<Role> findRolesByMember(UUID userId, UUID serverId);
}