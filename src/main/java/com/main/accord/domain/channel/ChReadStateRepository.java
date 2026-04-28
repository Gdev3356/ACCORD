package com.main.accord.domain.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChReadStateRepository extends JpaRepository<ChReadState, ChReadStateId> {

    Optional<ChReadState> findByIdChannelAndIdUser(UUID channelId, UUID userId);

    // ── Typed projections — replaces all Object[] returns ────────────────────

    /**
     * Unread message count per channel for a given server and user.
     * Previously returned List<Object[]>{ channelId::text, count } which required
     * manual UUID parsing in MessageService. Now fully typed.
     */
    @Query(nativeQuery = true, value = """
        SELECT m.id_channel::text AS channelId,
               COUNT(*)           AS unreadCount
        FROM   ms_message m
        JOIN   ch_channel c   ON c.id_channel = m.id_channel
                             AND c.id_server  = :serverId
        LEFT JOIN ch_read_state r ON r.id_channel = m.id_channel
                                 AND r.id_user    = :userId
        WHERE  m.st_deleted = FALSE
          AND  (r.dt_last_read IS NULL OR m.dt_created > r.dt_last_read)
        GROUP  BY m.id_channel
        HAVING COUNT(*) > 0
    """)
    List<ChannelUnreadCount> countUnreadPerChannelInServer(
            @Param("serverId") UUID serverId,
            @Param("userId")   UUID userId);

    /**
     * Unread count rolled up per server (used for server sidebar badges).
     * Previously returned List<Object[]>{ id_server, count }.
     */
    @Query(nativeQuery = true, value = """
        SELECT c.id_server::text AS serverId,
               COUNT(m.id_message) AS unreadCount
        FROM ms_message m
        JOIN ch_channel c ON c.id_channel = m.id_channel
        LEFT JOIN ch_read_state r
            ON r.id_channel = m.id_channel
           AND r.id_user    = :userId
        WHERE c.id_server IN :serverIds
          AND m.st_deleted = false
          AND (r.dt_last_read IS NULL OR m.dt_created > r.dt_last_read)
        GROUP BY c.id_server
    """)
    List<ServerUnreadCount> countUnreadPerServer(
            @Param("userId")    UUID userId,
            @Param("serverIds") List<UUID> serverIds);

    // ── Projection interfaces ─────────────────────────────────────────────────

    interface ChannelUnreadCount {
        String getChannelId();    // UUID as text — parse with UUID.fromString()
        Long   getUnreadCount();
    }

    interface ServerUnreadCount {
        String getServerId();     // UUID as text
        Long   getUnreadCount();
    }
}