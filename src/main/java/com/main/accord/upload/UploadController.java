package com.main.accord.upload;

import com.main.accord.common.ApiResponse;
import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.dm.DmAttachmentRepository;
import com.main.accord.domain.dm.DmMessage;
import com.main.accord.domain.dm.DmMessageRepository;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;
    private final DmAttachmentRepository dmAttachmentRepository;
    private final DmMessageRepository dmMessageRepository;

    @PostMapping("/pfp")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadPfp(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AccordPrincipal principal) throws IOException {

        String url = uploadService.uploadPfp(principal.userId(), file);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url)));
    }

    @PostMapping("/attachment/{messageId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadAttachment(
            @PathVariable UUID messageId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AccordPrincipal principal) throws IOException {

        String url = uploadService.uploadAttachment(messageId, file);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url)));
    }

    @PostMapping("/emoji/{serverId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadEmoji(
            @PathVariable UUID serverId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @AuthenticationPrincipal AccordPrincipal principal) throws IOException {

        String url = uploadService.uploadEmoji(serverId, principal.userId(), name, file);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url)));
    }

    @PostMapping("/dm-attachment/{messageId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> uploadDmAttachment(
            @PathVariable UUID messageId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal AccordPrincipal principal) throws IOException {

        // ── Security: only the message author may attach files ────────────────
        DmMessage message = dmMessageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Message not found."));

        if (!message.getIdAuthor().equals(principal.userId())) {
            throw new ForbiddenException("You can only attach files to your own messages.");
        }
        // ─────────────────────────────────────────────────────────────────────

        String url = uploadService.uploadDmAttachment(messageId, file);
        return ResponseEntity.ok(ApiResponse.ok(Map.of("url", url)));
    }
}