package com.main.accord.domain.server;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, Member.MemberId> {

    List<Member> findByIdServer(UUID serverId);

    Optional<Member> findByIdServerAndIdUser(UUID serverId, UUID userId);

    boolean existsByIdServerAndIdUser(UUID serverId, UUID userId);

    @Query("SELECT s FROM Server s WHERE s.idServer = :serverId")
    Server findServer(UUID serverId);
}