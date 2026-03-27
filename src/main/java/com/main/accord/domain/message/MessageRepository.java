package com.main.accord.domain.message;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Modifying
    @Transactional
    @Query(value = """
    UPDATE MS_MESSAGE
    SET    TS_CONTENT = to_tsvector('english', :plaintext)
    WHERE  ID_MESSAGE = :id
      AND  :plaintext IS NOT NULL
      AND  LENGTH(TRIM(:plaintext)) > 0
""", nativeQuery = true)
    void updateSearchVector(@Param("id")        UUID id,
                            @Param("plaintext") String plaintext);

    @Query(value = """
    SELECT
        ID_MESSAGE,
        ID_CHANNEL,
        ID_AUTHOR,
        ID_REPLY_TO,
        DS_CONTENT,
        ST_EDITED,
        ST_PINNED,
        ST_DELETED,
        DT_CREATED,
        DT_EDITED
    FROM  MS_MESSAGE
    WHERE ID_CHANNEL = :channelId
      AND ST_DELETED  = FALSE
      AND TS_CONTENT  @@ plainto_tsquery('english', :query)
    ORDER BY ts_rank(TS_CONTENT, plainto_tsquery('english', :query)) DESC
    LIMIT :limit
""", nativeQuery = true)
    List<Message> fullTextSearch(@Param("channelId") UUID channelId,
                                 @Param("query")     String query,
                                 @Param("limit")     int limit);
}