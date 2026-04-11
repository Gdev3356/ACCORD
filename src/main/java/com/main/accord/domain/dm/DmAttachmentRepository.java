package com.main.accord.domain.dm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DmAttachmentRepository extends JpaRepository<DmAttachment, UUID> {

    List<DmAttachment> findByIdMessage(UUID messageId);

    // --- Deduplication ---
    Optional<DmAttachment> findByDsSha256(String sha256);

    // --- Cleanup job: find attachments nobody has viewed in 30 days ---
    @Query("SELECT a FROM DmAttachment a WHERE a.dtLastAccessed < :cutoff")
    List<DmAttachment> findStaleAttachments(@Param("cutoff") ZonedDateTime cutoff);

    // --- Touch last-accessed for a whole page of messages in one UPDATE ---
    @Modifying
    @Query("""
        UPDATE DmAttachment a
        SET a.dtLastAccessed = :now
        WHERE a.idMessage IN :messageIds
    """)
    void touchByMessageIds(@Param("messageIds") List<UUID> messageIds,
                           @Param("now") ZonedDateTime now);
}