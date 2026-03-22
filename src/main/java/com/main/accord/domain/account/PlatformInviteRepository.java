package com.main.accord.domain.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformInviteRepository extends JpaRepository<PlatformInvite, UUID> {
    Optional<PlatformInvite> findByDsCode(String code);
    List<PlatformInvite> findByIdCreatorOrderByDtCreatedDesc(UUID creatorId);
}