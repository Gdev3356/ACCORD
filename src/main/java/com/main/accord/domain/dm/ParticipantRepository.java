package com.main.accord.domain.dm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
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

    @Query("""
    SELECT p FROM DmParticipant p
    WHERE p.idConversation IN :conversationIds
      AND p.idUser <> :userId
      AND p.dtLeft IS NULL
""")
    List<Participant> findOtherParticipantsIn(
            @Param("conversationIds") List<UUID> conversationIds,
            @Param("userId") UUID userId
    );

    @Query("""
        SELECT DISTINCT p2.idUser
        FROM DmParticipant p1
        JOIN DmParticipant p2 ON p1.idConversation = p2.idConversation
        WHERE p1.idUser = :userId
          AND p1.dtLeft IS NULL
          AND p2.idUser != :userId
          AND p2.dtLeft IS NULL
    """)
    List<UUID> findOtherParticipantsInAllDMs(@Param("userId") UUID userId);

    @Query(value = """
        SELECT DISTINCT p2.id_user
        FROM "DM_PARTICIPANT" p1
        INNER JOIN "DM_PARTICIPANT" p2 ON p1.id_conversation = p2.id_conversation
        WHERE p1.id_user = :userId
          AND p1.dt_left IS NULL
          AND p2.id_user != :userId
          AND p2.dt_left IS NULL
        LIMIT 500
    """, nativeQuery = true)
    Set<UUID> findRecentDMParticipants(@Param("userId") UUID userId);
}