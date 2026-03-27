package com.main.accord.security;

import com.main.accord.domain.dm.DmMessage;
import com.main.accord.domain.dm.DmMessageRepository;
import com.main.accord.domain.message.Message;
import com.main.accord.domain.message.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EncryptionMigrationService {

    private final DmMessageRepository dmRepository;
    private final MessageRepository msRepository;
    private final EncryptionService encryptionService;

    private static final int BATCH_SIZE = 100;

    /**
     * Call this from a secure Admin Controller or a Startup Listener.
     */
    public void migrateAllMessages() {
        log.info("Starting encryption migration...");

        migrateDmMessages();
        migrateServerMessages();

        log.info("Encryption migration successfully completed.");
    }

    private void migrateDmMessages() {
        int page = 0;
        long totalProcessed = 0;
        List<DmMessage> messages;

        do {
            messages = dmRepository.findAll(PageRequest.of(page, BATCH_SIZE)).getContent();
            for (DmMessage msg : messages) {
                if (processDmMessage(msg)) totalProcessed++;
            }
            page++;
            log.info("Processed {} DM messages...", totalProcessed);
        } while (!messages.isEmpty());
    }

    private void migrateServerMessages() {
        int page = 0;
        long totalProcessed = 0;
        List<Message> messages;

        do {
            messages = msRepository.findAll(PageRequest.of(page, BATCH_SIZE)).getContent();
            for (Message msg : messages) {
                if (processMsMessage(msg)) totalProcessed++;
            }
            page++;
            log.info("Processed {} Server messages...", totalProcessed);
        } while (!messages.isEmpty());
    }

    @Transactional
    protected boolean processDmMessage(DmMessage msg) {
        String raw = msg.getDsContent();
        if (raw == null || msg.getStDeleted()) return false;

        try {
            // If this succeeds, it's already encrypted. Skip.
            encryptionService.decrypt(raw);
            return false;
        } catch (Exception e) {
            // It's plaintext. Encrypt it.
            String encrypted = encryptionService.encrypt(raw);
            msg.setDsContent(encrypted);
            dmRepository.save(msg);

            // Sync search vector while we still have the 'raw' plaintext
            dmRepository.updateSearchVector(msg.getIdMessage(), raw);
            return true;
        }
    }

    @Transactional
    protected boolean processMsMessage(Message msg) {
        String raw = msg.getDsContent();
        if (raw == null || msg.getStDeleted()) return false;

        try {
            encryptionService.decrypt(raw);
            return false;
        } catch (Exception e) {
            String encrypted = encryptionService.encrypt(raw);
            msg.setDsContent(encrypted);
            msRepository.save(msg);
            msRepository.updateSearchVector(msg.getIdMessage(), raw);
            return true;
        }
    }
}