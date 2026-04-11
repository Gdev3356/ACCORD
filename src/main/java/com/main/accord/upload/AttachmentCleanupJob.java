package com.main.accord.upload;

import com.main.accord.domain.dm.DmAttachment;
import com.main.accord.domain.dm.DmAttachmentRepository;
import com.main.accord.domain.message.MsAttachment;
import com.main.accord.domain.message.MsAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentCleanupJob {

    private final DmAttachmentRepository dmAttachmentRepository;
    private final MsAttachmentRepository msAttachmentRepository;
    private final S3Client               s3Client;

    @Value("${supabase.storage.bucket}")
    private String bucket;

    @Value("${supabase.storage.public-url}")
    private String publicUrl;

    @Scheduled(cron = "0 0 3 * * *")
    public void purgeStaleAttachments() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(30);
        log.info("[Cleanup] Starting stale attachment purge (cutoff = {})", cutoff);

        int dmDeleted = purgeDm(dmAttachmentRepository.findStaleAttachments(cutoff));
        int msDeleted = purgeMs(msAttachmentRepository.findStaleAttachments(cutoff));

        log.info("[Cleanup] Done — DM: {} deleted, MS: {} deleted", dmDeleted, msDeleted);
    }

    private int purgeDm(List<DmAttachment> stale) {
        int count = 0;
        for (DmAttachment a : stale) {
            try {
                deleteFromS3(a.getDsUrl());
                dmAttachmentRepository.deleteById(a.getIdAttachment());
                count++;
            } catch (Exception e) {
                log.warn("[Cleanup] Failed to delete DM attachment {}: {}",
                        a.getIdAttachment(), e.getMessage());
            }
        }
        return count;
    }

    private int purgeMs(List<MsAttachment> stale) {
        int count = 0;
        for (MsAttachment a : stale) {
            try {
                deleteFromS3(a.getDsUrl());
                msAttachmentRepository.deleteById(a.getIdAttachment());
                count++;
            } catch (Exception e) {
                log.warn("[Cleanup] Failed to delete MS attachment {}: {}",
                        a.getIdAttachment(), e.getMessage());
            }
        }
        return count;
    }

    private void deleteFromS3(String url) {
        String prefix = publicUrl + "/" + bucket + "/";
        if (!url.startsWith(prefix)) {
            throw new IllegalArgumentException("URL does not match expected pattern: " + url);
        }
        String key = url.substring(prefix.length());
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build());
    }
}