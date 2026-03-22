package com.main.accord.domain.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    // Paginated fetch for a channel — most recent first
    @Query("""
        SELECT m FROM Message m
        WHERE m.idChannel = :channelId
          AND m.stDeleted = false
        ORDER BY m.dtCreated DESC
    """)
    List<Message> findByChannel(UUID channelId, Pageable pageable);

    // Cursor-based pagination (before a given message — for infinite scroll upward)
    @Query("""
        SELECT m FROM Message m
        WHERE m.idChannel = :channelId
          AND m.stDeleted = false
          AND m.dtCreated < (SELECT m2.dtCreated FROM Message m2 WHERE m2.idMessage = :beforeId)
        ORDER BY m.dtCreated DESC
    """)
    List<Message> findBeforeMessage(UUID channelId, UUID beforeId, Pageable pageable);
}