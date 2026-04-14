package com.main.accord.domain.server;

import com.main.accord.common.ApiResponse;
import com.main.accord.common.ForbiddenException;
import com.main.accord.permission.PermissionService;
import com.main.accord.permission.Permissions;
import com.main.accord.security.AccordPrincipal;
import com.main.accord.upload.UploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/servers/{serverId}/emojis")
@RequiredArgsConstructor
public class SvEmojiController {

    private final SvEmojiRepository emojiRepository;
    private final MemberRepository  memberRepository;
    private final PermissionService permissionService;
    private final UploadService     uploadService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<SvEmoji>>> list(
            @PathVariable UUID serverId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!memberRepository.existsByIdServerAndIdUser(serverId, principal.userId()))
            throw new ForbiddenException("Not a member.");
        return ResponseEntity.ok(ApiResponse.ok(emojiRepository.findByIdServer(serverId)));
    }

    @DeleteMapping("/{emojiId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable UUID serverId,
            @PathVariable UUID emojiId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        if (!permissionService.can(principal.userId(), null, serverId, Permissions.MANAGE_SERVER))
            throw new ForbiddenException("No permission.");
        uploadService.deleteEmoji(emojiId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}