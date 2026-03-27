package com.main.accord.upload;

import com.main.accord.common.AccordException;
import com.main.accord.domain.account.VisualsRepository;
import com.main.accord.domain.dm.DmAttachment;
import com.main.accord.domain.dm.DmAttachmentRepository;
import com.main.accord.domain.dm.DmMessageRepository;
import com.main.accord.domain.server.SvEmoji;
import com.main.accord.domain.server.SvEmojiRepository;
import com.main.accord.websocket.ChatHandler;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadService {

    private final S3Client          s3Client;
    private final VisualsRepository visualsRepository;
    private final DmAttachmentRepository dmAttachmentRepository;
    private final ChatHandler chatHandler;
    private final DmMessageRepository dmMessageRepository;

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

    private final SvEmojiRepository emojiRepository;  // add to constructor injection

    private static final long MAX_EMOJI_SIZE = 256 * 1024L; // 256KB — same as Discord

    private static final Set<String> ALLOWED_EMOJI_TYPES = Set.of(
            "image/png", "image/gif", "image/webp"
    );

    public String uploadEmoji(UUID serverId, UUID creatorId,
                              String name, MultipartFile file) throws IOException {
        if (file.isEmpty())
            throw new AccordException("File is empty.");
        if (file.getSize() > MAX_EMOJI_SIZE)
            throw new AccordException("Emoji must be under 256KB.");

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_EMOJI_TYPES.contains(contentType.toLowerCase()))
            throw new AccordException("Emoji must be PNG, GIF, or WebP.");

        // Validate name — alphanumeric + underscores only, like Discord
        if (!name.matches("[a-zA-Z0-9_]{2,50}"))
            throw new AccordException("Emoji name must be 2–50 alphanumeric characters or underscores.");

        if (emojiRepository.existsByIdServerAndDsName(serverId, name))
            throw new AccordException("An emoji with that name already exists in this server.");

        boolean animated = "image/gif".equals(contentType.toLowerCase());

        // For non-GIF images, resize to 128×128 like Discord
        byte[] bytes = animated ? file.getBytes() : resizeImage(file, 128, 128);
        String ext   = animated ? ".gif" : ".png";
        String key   = "emojis/" + serverId + "/" + name + ext;

        upload(key, bytes, animated ? "image/gif" : "image/png");
        String url = publicUrl + "/" + bucket + "/" + key;

        emojiRepository.save(SvEmoji.builder()
                .idServer(serverId)
                .idCreator(creatorId)
                .dsName(name)
                .dsUrl(url)
                .stAnimated(animated)
                .build());

        return url;
    }

    public void deleteEmoji(UUID emojiId) {
        emojiRepository.findById(emojiId).ifPresent(emoji -> {
            // Optionally delete from S3 here too
            emojiRepository.delete(emoji);
        });
    }

    public String uploadDmAttachment(UUID messageId, MultipartFile file) throws IOException {
        if (file.isEmpty())
            throw new AccordException("File is empty.");
        if (file.getSize() > MAX_ATTACHMENT_SIZE)
            throw new AccordException("Attachment must be under 25MB.");

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_ATTACHMENT_TYPES.contains(contentType.toLowerCase()))
            throw new AccordException("File type not allowed.");

        String ext = getExtension(file.getOriginalFilename());
        String key = "dm-attachments/" + messageId + "/" + UUID.randomUUID() + ext;
        upload(key, file.getBytes(), contentType);
        String url = publicUrl + "/" + bucket + "/" + key;

        // ── Resolve image dimensions if applicable ────────────────────────────────
        Integer width  = null;
        Integer height = null;
        if (contentType.startsWith("image/")) {
            try {
                BufferedImage img = ImageIO.read(file.getInputStream());
                if (img != null) {
                    width  = img.getWidth();
                    height = img.getHeight();
                }
            } catch (IOException ignored) {
                // Non-fatal — dimensions stay null; frontend falls back gracefully
            }
        }
        // ─────────────────────────────────────────────────────────────────────────

        dmAttachmentRepository.save(DmAttachment.builder()
                .idMessage(messageId)
                .dsUrl(url)
                .dsFilename(file.getOriginalFilename())
                .dsMimeType(contentType)
                .nrSizeBytes(file.getSize())
                .nrWidth(width)
                .nrHeight(height)
                .build());

// Broadcast the updated message so all participants see the attachment immediately
        dmMessageRepository.findById(messageId).ifPresent(msg ->
                chatHandler.broadcastToDm(
                        msg.getIdConversation(),
                        Map.of("type", "DM_MESSAGE_EDIT", "data", msg)
                )
        );

        return url;
    }

    private static final long MAX_BANNER_SIZE = 8 * 1024 * 1024L; // 8MB

    private static final Set<String> ALLOWED_BANNER_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    public String uploadBanner(UUID userId, MultipartFile file) throws IOException {
        if (file.isEmpty())
            throw new AccordException("File is empty.");
        if (file.getSize() > MAX_BANNER_SIZE)
            throw new AccordException("Banner must be under 8MB.");

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_BANNER_TYPES.contains(contentType.toLowerCase()))
            throw new AccordException("Banner must be JPEG, PNG, GIF, or WebP.");

        String ext = getExtension(file.getOriginalFilename());
        String key = "banners/" + userId + "/" + UUID.randomUUID() + ext;
        upload(key, file.getBytes(), contentType);
        String url = publicUrl + "/" + bucket + "/" + key;

        visualsRepository.findById(userId).ifPresent(v -> {
            v.setDsBannerUrl(url);
            visualsRepository.save(v);
        });

        return url;
    }
}