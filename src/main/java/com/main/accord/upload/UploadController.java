package com.main.accord.upload;

import com.main.accord.common.ApiResponse;
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
}
