package com.main.accord.domain.message;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EditHistoryRepository extends JpaRepository<EditHistory, UUID> {

    // Full edit history for a message, oldest first
    List<EditHistory> findByIdMessageOrderByDtEditedAsc(UUID messageId);
}