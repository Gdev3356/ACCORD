package com.main.accord.domain.server;

import com.main.accord.common.ApiResponse;
import com.main.accord.common.ForbiddenException;
import com.main.accord.common.NotFoundException;
import com.main.accord.security.AccordPrincipal;
import com.main.accord.upload.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/servers/{serverId}/emojis")
@RequiredArgsConstructor
public class EmojiController {

    private final SvEmojiRepository  emojiRepository;
    private final ServerRepository   serverRepository;   // to check ownership
    private final UploadService      uploadService;

    // GET /api/v1/servers/{serverId}/emojis
    @GetMapping
    public ResponseEntity<ApiResponse<List<SvEmoji>>> getEmojis(
            @PathVariable UUID serverId) {
        return ResponseEntity.ok(ApiResponse.ok(
                emojiRepository.findByIdServer(serverId)
        ));
    }

    // POST /api/v1/servers/{serverId}/emojis  (multipart)
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<SvEmoji>> addEmoji(
            @PathVariable UUID serverId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @AuthenticationPrincipal AccordPrincipal principal) throws IOException {

        assertManageEmojis(serverId, principal.userId());
        uploadService.uploadEmoji(serverId, principal.userId(), name, file);

        // Return the saved entity (uploadEmoji already persisted it)
        SvEmoji saved = emojiRepository
                .findByIdServer(serverId)
                .stream()
                .filter(e -> e.getDsName().equals(name))
                .findFirst()
                .orElseThrow();

        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    // DELETE /api/v1/servers/{serverId}/emojis/{emojiId}
    @DeleteMapping("/{emojiId}")
    public ResponseEntity<ApiResponse<Void>> deleteEmoji(
            @PathVariable UUID serverId,
            @PathVariable UUID emojiId,
            @AuthenticationPrincipal AccordPrincipal principal) {

        assertManageEmojis(serverId, principal.userId());
        uploadService.deleteEmoji(emojiId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    // Only server owner can manage emojis for now
    // Swap this for a permission bitmask check if you add roles later
    private void assertManageEmojis(UUID serverId, UUID userId) {
        var server = serverRepository.findById(serverId)
                .orElseThrow(() -> new NotFoundException("Server not found."));
        if (!server.getIdOwner().equals(userId))
            throw new ForbiddenException("You don't have permission to manage emojis.");
    }
}