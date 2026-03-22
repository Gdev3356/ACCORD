package com.main.accord.upload;

import com.main.accord.common.AccordException;
import com.main.accord.domain.account.VisualsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadService {

    private final S3Client          s3Client;
    private final VisualsRepository visualsRepository;

    @Value("${supabase.storage.bucket}")
    private String bucket;

    @Value("${supabase.storage.public-url}")
    private String publicUrl;

    // Allowed MIME types for profile pictures
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    // Allowed MIME types for message attachments
    private static final Set<String> ALLOWED_ATTACHMENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm",
            "audio/mpeg", "audio/ogg", "audio/wav",
            "application/pdf",
            "text/plain",
            "application/zip"
    );

    private static final long MAX_PFP_SIZE        = 5  * 1024 * 1024L; // 5MB
    private static final long MAX_ATTACHMENT_SIZE = 25 * 1024 * 1024L; // 25MB

    public String uploadPfp(UUID userId, MultipartFile file) throws IOException {
        // ── Validate ──────────────────────────────────────────────────────────
        if (file.isEmpty()) {
            throw new AccordException("File is empty.");
        }
        if (file.getSize() > MAX_PFP_SIZE) {
            throw new AccordException("Profile picture must be under 5MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new AccordException("Profile picture must be a JPEG, PNG, GIF, or WebP image.");
        }

        // ── Resize & upload ───────────────────────────────────────────────────
        byte[] resized = resizeImage(file, 256, 256);
        String key     = "pfp/" + userId + ".jpg";
        upload(key, resized, "image/jpeg");
        String url = publicUrl + "/" + bucket + "/" + key;

        // ── Persist URL ───────────────────────────────────────────────────────
        visualsRepository.findById(userId).ifPresent(v -> {
            v.setDsPfpUrl(url);
            visualsRepository.save(v);
        });

        return url;
    }

    public String uploadAttachment(UUID messageId, MultipartFile file) throws IOException {
        // ── Validate ──────────────────────────────────────────────────────────
        if (file.isEmpty()) {
            throw new AccordException("File is empty.");
        }
        if (file.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new AccordException("Attachment must be under 25MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_ATTACHMENT_TYPES.contains(contentType.toLowerCase())) {
            throw new AccordException("File type not allowed.");
        }

        // ── Upload ────────────────────────────────────────────────────────────
        String ext = getExtension(file.getOriginalFilename());
        String key = "attachments/" + messageId + "/" + UUID.randomUUID() + ext;
        upload(key, file.getBytes(), contentType);
        return publicUrl + "/" + bucket + "/" + key;
    }

    private void upload(String key, byte[] data, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromBytes(data)
        );
    }

    private byte[] resizeImage(MultipartFile file, int w, int h) throws IOException {
        BufferedImage src = ImageIO.read(file.getInputStream());
        if (src == null) {
            throw new AccordException("Could not read image — file may be corrupt or unsupported.");
        }
        BufferedImage dest = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dest.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(dest, "jpg", out);
        return out.toByteArray();
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.'));
    }
}