package com.main.accord.domain.dm;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DmAttachmentRepository extends JpaRepository<DmAttachment, UUID> {
    List<DmAttachment> findByIdMessage(UUID messageId);
}