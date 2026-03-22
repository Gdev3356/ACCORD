package com.main.accord.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("""
        SELECT n FROM Notification n
        WHERE n.idUser = :userId
          AND n.stRead = false
        ORDER BY n.dtCreated DESC
    """)
    List<Notification> findUnreadByUser(UUID userId);

    @Query("""
        SELECT n FROM Notification n
        WHERE n.idUser = :userId
        ORDER BY n.dtCreated DESC
    """)
    List<Notification> findAllByUser(UUID userId);
}