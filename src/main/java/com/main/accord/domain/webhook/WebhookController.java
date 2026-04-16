package com.main.accord.domain.webhook;

import com.main.accord.common.ApiResponse;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/servers/{serverId}/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Webhook>>> getWebhooks(
            @PathVariable UUID serverId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                webhookService.getServerWebhooks(serverId, principal.userId())
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Webhook>> createWebhook(
            @PathVariable UUID serverId,
            @RequestBody CreateWebhookRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                webhookService.createWebhook(
                        serverId, req.channelId(), principal.userId(),
                        req.name(), req.avatarUrl(), req.bio(), req.bannerUrl(), req.color(), req.eventType(), req.messageTemplate()
                )
        ));
    }

    @PatchMapping("/{webhookId}")
    public ResponseEntity<ApiResponse<Webhook>> updateWebhook(
            @PathVariable UUID serverId,
            @PathVariable UUID webhookId,
            @RequestBody UpdateWebhookRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                webhookService.updateWebhook(webhookId, principal.userId(), req)
        ));
    }

    @DeleteMapping("/{webhookId}")
    public ResponseEntity<ApiResponse<Void>> deleteWebhook(
            @PathVariable UUID serverId,
            @PathVariable UUID webhookId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        webhookService.deleteWebhook(webhookId, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    public record CreateWebhookRequest(
            UUID channelId,
            String name,
            String avatarUrl,
            String bio,
            String bannerUrl,
            Integer color,
            String eventType,
            String messageTemplate
    ) {}

    public record UpdateWebhookRequest(
            String name,
            UUID channelId,
            String avatarUrl,
            String bio,
            String bannerUrl,
            Integer color,
            String eventType,
            String messageTemplate,
            Boolean active
    ) {}
}