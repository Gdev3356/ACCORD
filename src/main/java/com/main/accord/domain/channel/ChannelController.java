package com.main.accord.domain.channel;

import com.main.accord.common.ApiResponse;
import com.main.accord.security.AccordPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/servers/{serverId}/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Channel>>> getChannels(
            @PathVariable UUID serverId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                channelService.getChannels(serverId, principal.userId())
        ));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Channel>> createChannel(
            @PathVariable UUID serverId,
            @RequestBody ChannelService.CreateChannelRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                channelService.createChannel(serverId, principal.userId(), req)
        ));
    }

    @PatchMapping("/{channelId}")
    public ResponseEntity<ApiResponse<Channel>> updateChannel(
            @PathVariable UUID serverId,
            @PathVariable UUID channelId,
            @RequestBody ChannelService.UpdateChannelRequest req,
            @AuthenticationPrincipal AccordPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                channelService.updateChannel(serverId, channelId, principal.userId(), req)
        ));
    }

    @DeleteMapping("/{channelId}")
    public ResponseEntity<ApiResponse<Void>> deleteChannel(
            @PathVariable UUID serverId,
            @PathVariable UUID channelId,
            @AuthenticationPrincipal AccordPrincipal principal) {
        channelService.deleteChannel(serverId, channelId, principal.userId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}