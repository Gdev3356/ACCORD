package com.main.accord.domain.dm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    // Find an existing 1-on-1 DM between two users
    @Query("""
        SELECT c FROM Conversation c
        WHERE c.stGroup = false
          AND EXISTS (
              SELECT 1 FROM DmParticipant p1
              WHERE p1.idConversation = c.idConversation
                AND p1.idUser = :userA
                AND p1.dtLeft IS NULL
          )
          AND EXISTS (
              SELECT 1 FROM DmParticipant p2
              WHERE p2.idConversation = c.idConversation
                AND p2.idUser = :userB
                AND p2.dtLeft IS NULL
          )
    """)
    Optional<Conversation> findDirectBetween(UUID userA, UUID userB);

    // All conversations a user is currently part of
    @Query("""
        SELECT c FROM Conversation c
        JOIN DmParticipant p ON p.idConversation = c.idConversation
        WHERE p.idUser = :userId
          AND p.dtLeft IS NULL
        ORDER BY c.dtCreated DESC
    """)
    List<Conversation> findAllByParticipant(UUID userId);
}