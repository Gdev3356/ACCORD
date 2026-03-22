package com.main.accord.domain.message;

import com.main.accord.common.ApiResponse;
import com.main.accord.security.AccordPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/channels/{channelId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Message>>> getMessages(
            @PathVariable UUID channelId,
            @RequestParam(required = false) UUID before,
            @RequestParam(defaultValue = "50") int limit,
            @AuthenticationPrincipal AccordPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                messageService.getMessages(channelId, principal.userId(), before, limit)
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Message>> sendMessage(
            @PathVariable UUID channelId,
            @Valid @RequestBody SendMessageRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                messageService.sendMessage(channelId, principal.userId(), req.content())
        ));
    }

    @PatchMapping("/{messageId}")
    public ResponseEntity<ApiResponse<Message>> editMessage(
            @PathVariable UUID channelId,
            @PathVariable UUID messageId,
            @Valid @RequestBody EditMessageRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                messageService.editMessage(messageId, principal.userId(), req.content())
        ));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            @PathVariable UUID channelId,
            @PathVariable UUID messageId,
            @AuthenticationPrincipal AccordPrincipal principal) {

        messageService.deleteMessage(messageId, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    public record SendMessageRequest(@jakarta.validation.constraints.NotBlank String content) {}
    public record EditMessageRequest(@jakarta.validation.constraints.NotBlank String content) {}
}
