package com.main.accord.domain.webhook;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface WebhookRepository extends JpaRepository<Webhook, UUID> {

    List<Webhook> findByIdServerAndStActiveTrue(UUID serverId);

    List<Webhook> findByIdServerAndTpEventAndStActiveTrue(UUID serverId, String tpEvent);

    @Query("SELECT w FROM Webhook w WHERE w.stActive = true AND w.tpEvent = :event")
    List<Webhook> findAllActiveByEvent(@Param("event") String event);

    @Query("SELECT w FROM Webhook w " +
            "WHERE w.idServer IN :serverIds " +
            "AND w.tpEvent = :event " +
            "AND w.stActive = true")
    List<Webhook> findByServerIdsAndEventAndActiveTrue(@Param("serverIds") List<UUID> serverIds,
                                                       @Param("event") String event);
}