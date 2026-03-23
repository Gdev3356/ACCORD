package com.main.accord.domain.dm;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
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
}