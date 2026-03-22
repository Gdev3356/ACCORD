package com.main.accord.domain.server;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MemberRoleRepository extends JpaRepository<MemberRole, MemberRole.MemberRoleId> {

    @Query("""
        SELECT r FROM Role r
        JOIN MemberRole mr ON mr.idRole = r.idRole
        WHERE mr.idUser = :userId AND mr.idServer = :serverId
    """)
    List<Role> findRolesByMember(UUID userId, UUID serverId);
}