package com.main.accord.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminControllerMigration {

    private final EncryptionMigrationService migrationService;

    // We'll reuse your JWT secret or a dedicated ADMIN_KEY from your .env
    @Value("${accord.jwt.secret}")
    private String adminSecret;

    @PostMapping("/migrate-encryption")
    public ResponseEntity<String> triggerMigration(
            @RequestHeader("X-Admin-Token") String token) {

        // Basic security check to ensure only you can run this
        if (!adminSecret.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Invalid admin token.");
        }

        try {
            // We run this in a separate thread so the request doesn't time out
            new Thread(migrationService::migrateAllMessages).start();

            return ResponseEntity.ok("Migration started in the background. Check logs for progress.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Failed to start migration: " + e.getMessage());
        }
    }
}