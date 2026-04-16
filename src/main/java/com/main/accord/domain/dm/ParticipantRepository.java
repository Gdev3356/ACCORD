package com.main.accord.domain.dm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
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

    @Query("SELECT DISTINCT p.idUser FROM Participant p " +
            "WHERE p.idConversation IN (" +
            "   SELECT p2.idConversation FROM Participant p2 " +
            "   WHERE p2.idUser = :userId AND p2.dtLeft IS NULL" +
            ") " +
            "AND p.idUser != :userId AND p.dtLeft IS NULL " +
            "AND p.idConversation IN (" +
            "   SELECT DISTINCT dm.idConversation FROM DmMessage dm " +
            "   WHERE dm.dtCreated > :recent OR dm.idConversation IN (" +
            "       SELECT DISTINCT p3.idConversation FROM Participant p3 " +
            "       WHERE p3.idUser = :userId AND p3.dtJoined > :recent" +
            "   )" +
            ")")
    List<UUID> findOtherParticipantsInAllDMs(@Param("userId") UUID userId, @Param("recent") OffsetDateTime recent);
}