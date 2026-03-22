package com.main.accord.domain.channel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChannelRepository extends JpaRepository<Channel, UUID> {

    // All channels for a server, sorted by position (categories + their children)
    List<Channel> findByIdServerOrderByNrPositionAsc(UUID serverId);

    // Children of a category channel
    List<Channel> findByIdParentOrderByNrPositionAsc(UUID parentId);

    Optional<Channel> findByIdChannelAndIdServer(UUID channelId, UUID serverId);

    boolean existsByIdServerAndDsName(UUID serverId, String dsName);
}