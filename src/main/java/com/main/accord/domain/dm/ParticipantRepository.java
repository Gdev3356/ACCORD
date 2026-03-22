package com.main.accord.domain.dm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, Participant.ParticipantId> {

    List<Participant> findByIdConversationAndDtLeftIsNull(UUID conversationId);

    @Query("""
        SELECT COUNT(p) > 0 FROM Participant p
        WHERE p.idConversation = :conversationId
          AND p.idUser = :userId
          AND p.dtLeft IS NULL
    """)
    boolean isActiveParticipant(UUID conversationId, UUID userId);
}