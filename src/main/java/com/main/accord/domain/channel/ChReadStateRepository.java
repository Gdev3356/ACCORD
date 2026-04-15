package com.main.accord.domain.channel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChReadStateRepository extends JpaRepository<ChReadState, ChReadStateId> {

    Optional<ChReadState> findByIdChannelAndIdUser(UUID channelId, UUID userId);

    /**
     * Single query: counts unread messages per channel for every channel in a server.
     * Joins last-read message by PK (fast), groups by channel, skips channels with 0 unread.
     * Returns Object[]{ channelId::text, count }.
     */
    @Query(nativeQuery = true, value = """
        SELECT m.id_channel::text, COUNT(*) AS cnt
        FROM   ms_message m
        JOIN   ch_channel c   ON c.id_channel = m.id_channel
                             AND c.id_server = :serverId
        LEFT JOIN ch_read_state r ON r.id_channel = m.id_channel
                                 AND r.id_user    = :userId
        WHERE  m.st_deleted = FALSE
          -- Use the timestamp directly, it's safer than joining the message table
          AND  (r.dt_last_read IS NULL OR m.dt_created > r.dt_last_read)
        GROUP  BY m.id_channel
        HAVING COUNT(*) > 0
    """)
    List<Object[]> countUnreadPerChannelInServer(
            @Param("serverId") UUID serverId,
            @Param("userId")   UUID userId);


    @Query(value = """
    SELECT c.id_server, COUNT(m.id_message)
    FROM ms_message m
    JOIN CH_CHANNEL c ON c.id_channel = m.id_channel
    LEFT JOIN ch_read_state r
        ON r.id_channel = m.id_channel
        AND r.id_user = :userId
    WHERE c.id_server IN :serverIds
      AND m.st_deleted = false
      AND (r.dt_last_read IS NULL OR m.dt_created > r.dt_last_read)
    GROUP BY c.id_server
""", nativeQuery = true)
    List<Object[]> countUnreadPerServer(
            @Param("userId")    UUID userId,
            @Param("serverIds") List<UUID> serverIds
    );
}