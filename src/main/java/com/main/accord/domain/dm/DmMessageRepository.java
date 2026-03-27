package com.main.accord.domain.dm;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DmMessageRepository extends JpaRepository<DmMessage, UUID> {

    @Query("""
        SELECT m FROM DmMessage m
        WHERE m.idConversation = :conversationId
          AND m.stDeleted = false
        ORDER BY m.dtCreated DESC
    """)
    List<DmMessage> findByConversation(UUID conversationId, Pageable pageable);

    @Query("""
        SELECT m FROM DmMessage m
        WHERE m.idConversation = :conversationId
          AND m.stDeleted = false
          AND m.dtCreated < (
              SELECT m2.dtCreated FROM DmMessage m2
              WHERE m2.idMessage = :beforeId
          )
        ORDER BY m.dtCreated DESC
    """)
    List<DmMessage> findBeforeMessage(UUID conversationId, UUID beforeId, Pageable pageable);

        @Query("""
        SELECT m FROM DmMessage m
        WHERE m.idConversation = :conversationId
          AND m.stDeleted = false
          AND LOWER(m.dsContent) LIKE CONCAT('%', :query, '%')
        ORDER BY m.dtCreated DESC
    """)
    List<DmMessage> searchContent(UUID conversationId, String query, Pageable pageable);

    // For read state service
    @Query("SELECT m FROM DmMessage m WHERE m.idConversation = :convId AND m.stDeleted = false ORDER BY m.dtCreated DESC LIMIT 1")
    Optional<DmMessage> findLatestByConversation(UUID convId);

    // For reply previews
    Optional<DmMessage> findByIdMessage(UUID messageId); // likely already exists as findById

    @Query("SELECT COUNT(m) FROM DmMessage m WHERE m.idConversation = :convId AND m.stDeleted = false AND m.dtCreated > :since")
    long countUnreadSince(UUID convId, OffsetDateTime since);

    // Write the tsvector after encrypt/save
    @Modifying
    @Transactional
    @Query(value = """
    UPDATE DM_MESSAGE
    SET    TS_CONTENT = to_tsvector('english', :plaintext)
    WHERE  ID_MESSAGE = :id
      AND  :plaintext IS NOT NULL
      AND  LENGTH(TRIM(:plaintext)) > 0
""", nativeQuery = true)
    void updateSearchVector(@Param("id")        UUID id,
                            @Param("plaintext") String plaintext);

    // Ranked full-text search
    @Query(value = """
    SELECT
        ID_MESSAGE,
        ID_CONVERSATION,
        ID_AUTHOR,
        ID_REPLY_TO,
        DS_CONTENT,
        ST_EDITED,
        ST_DELETED,
        DT_CREATED,
        DT_EDITED,
        ID_FORWARDED_FROM
    FROM  DM_MESSAGE
    WHERE ID_CONVERSATION = :convId
      AND ST_DELETED      = FALSE
      AND TS_CONTENT      @@ plainto_tsquery('english', :query)
    ORDER BY ts_rank(TS_CONTENT, plainto_tsquery('english', :query)) DESC
    LIMIT :limit
""", nativeQuery = true)
    List<DmMessage> fullTextSearch(@Param("convId") UUID convId,
                                   @Param("query")  String query,
                                   @Param("limit")  int limit);
}