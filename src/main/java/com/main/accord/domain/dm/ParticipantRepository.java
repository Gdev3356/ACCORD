package com.main.accord.domain.dm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, Participant.ParticipantId> {

    List<Participant> findByIdConversationAndDtLeftIsNull(UUID conversationId);

    @Query("""
        SELECT COUNT(p) FROM DmParticipant p
        WHERE p.idConversation = :conversationId
          AND p.idUser = :userId
          AND p.dtLeft IS NULL
    """)
    long countActiveParticipant(UUID conversationId, UUID userId);

    default boolean isActiveParticipant(UUID conversationId, UUID userId) {
        return countActiveParticipant(conversationId, userId) > 0;
    }

    @Query("""
    SELECT p.idUser FROM DmParticipant p
    WHERE p.idConversation = :convId
      AND p.idUser <> :userId
    LIMIT 1
""")
    UUID findOtherParticipant(
            @Param("convId") UUID convId,
            @Param("userId") UUID userId
    );
}