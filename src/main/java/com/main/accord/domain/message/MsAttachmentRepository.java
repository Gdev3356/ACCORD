package com.main.accord.domain.message;

import com.main.accord.domain.message.MsAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MsAttachmentRepository extends JpaRepository<MsAttachment, UUID> {

    List<MsAttachment> findByIdMessage(UUID messageId);

    Optional<MsAttachment> findByDsSha256(String sha256);

    @Query("SELECT a FROM MsAttachment a WHERE a.dtLastAccessed < :cutoff")
    List<MsAttachment> findStaleAttachments(@Param("cutoff") ZonedDateTime cutoff);

    @Modifying
    @Query("""
        UPDATE MsAttachment a
        SET a.dtLastAccessed = :now
        WHERE a.idMessage IN :messageIds
    """)
    void touchByMessageIds(@Param("messageIds") List<UUID> messageIds,
                           @Param("now") ZonedDateTime now);
}