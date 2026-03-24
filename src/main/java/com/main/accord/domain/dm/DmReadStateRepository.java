package com.main.accord.domain.dm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DmReadStateRepository extends JpaRepository<DmReadState, DmReadStateId> {

    Optional<DmReadState> findByIdConversationAndIdUser(UUID conversationId, UUID userId);

    // How many conversations have unread messages for a user
    @Query("""
        SELECT r.idConversation FROM DmReadState r
        JOIN DmMessage m ON m.idConversation = r.idConversation
        WHERE r.idUser = :userId
          AND m.stDeleted = false
          AND (r.idLastReadMsg IS NULL OR m.dtCreated > (
              SELECT m2.dtCreated FROM DmMessage m2 WHERE m2.idMessage = r.idLastReadMsg
          ))
        GROUP BY r.idConversation
    """)
    List<UUID> findConversationsWithUnread(UUID userId);
}