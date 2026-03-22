package com.main.accord.domain.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PermissionOverrideRepository extends JpaRepository<PermissionOverride, PermissionOverride.PermissionOverrideId> {

    @Query("SELECT p FROM PermissionOverride p WHERE p.idChannel = :channelId AND p.idRole IN :roleIds")
    List<PermissionOverride> findByChannelAndRoles(UUID channelId, List<UUID> roleIds);

    Optional<PermissionOverride> findByIdChannelAndIdUser(UUID channelId, UUID userId);

    default Optional<PermissionOverride> findByChannelAndUser(UUID channelId, UUID userId) {
        return findByIdChannelAndIdUser(channelId, userId);
    }
}