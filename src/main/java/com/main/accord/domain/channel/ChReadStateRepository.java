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
        JOIN   ch_channel c   ON c.id_channel   = m.id_channel
                             AND c.id_server     = :serverId
        LEFT JOIN ch_read_state r ON r.id_channel = m.id_channel
                                 AND r.id_user    = :userId
        LEFT JOIN ms_message last  ON last.id_message = r.id_last_read_msg
        WHERE  m.st_deleted = FALSE
          AND  (last.dt_created IS NULL OR m.dt_created > last.dt_created)
        GROUP  BY m.id_channel
        HAVING COUNT(*) > 0
    """)
    List<Object[]> countUnreadPerChannelInServer(
            @Param("serverId") UUID serverId,
            @Param("userId")   UUID userId);
}