package com.main.accord.upload;

import com.main.accord.common.AccordException;
import com.main.accord.domain.account.VisualsRepository;
import com.main.accord.domain.dm.DmAttachment;
import com.main.accord.domain.dm.DmAttachmentRepository;
import com.main.accord.domain.dm.DmMessageRepository;
import com.main.accord.domain.dm.DmService;
import com.main.accord.domain.message.MsAttachment;
import com.main.accord.domain.message.MsAttachmentRepository;
import com.main.accord.domain.server.SvEmoji;
import com.main.accord.domain.server.SvEmojiRepository;
import com.main.accord.security.EncryptionService;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadService {

    private final S3Client               s3Client;
    private final VisualsRepository      visualsRepository;
    private final DmAttachmentRepository dmAttachmentRepository;
    private final MsAttachmentRepository msAttachmentRepository;
    private final ChatHandler            chatHandler;
    private final DmMessageRepository    dmMessageRepository;
    private final EncryptionService      encryptionService;
    private final DmService              dmService;
    private final SvEmojiRepository      emojiRepository;
    private final VideoCompressor        videoCompressor;

    @Value("${supabase.storage.bucket}")
    private String bucket;

    @Value("${supabase.storage.public-url}")
    private String publicUrl;

    // ── Limits ────────────────────────────────────────────────────────────────
    private static final long MAX_PFP_SIZE        = 5   * 1024 * 1024L;  // 5 MB
    private static final long MAX_BANNER_SIZE     = 8   * 1024 * 1024L;  // 8 MB
    private static final long MAX_ATTACHMENT_SIZE = 25  * 1024 * 1024L;  // 25 MB
    private static final long MAX_EMOJI_SIZE      = 256 * 1024L;          // 256 KB

    // Cap the longest edge of any image attachment before storing
    private static final int ATTACHMENT_IMAGE_MAX_DIM = 1920;

    // ── Allowed types ─────────────────────────────────────────────────────────
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );
    private static final Set<String> ALLOWED_ATTACHMENT_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
            "video/mp4", "video/webm",
            "audio/mpeg", "audio/ogg", "audio/wav",
            "application/pdf", "text/plain", "application/zip"
    );
    private static final Set<String> ALLOWED_BANNER_TYPES = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );
    private static final Set<String> ALLOWED_EMOJI_TYPES = Set.of(
            "image/png", "image/gif", "image/webp"
    );

    // ── Banner dimensions ─────────────────────────────────────────────────────
    private static final int BANNER_WIDTH  = 1500;
    private static final int BANNER_HEIGHT = 500;

    // =========================================================================
    // PFP
    // =========================================================================

    public String uploadPfp(UUID userId, MultipartFile file) throws IOException {
        if (file.isEmpty())
            throw new AccordException("File is empty.");
        if (file.getSize() > MAX_PFP_SIZE)
            throw new AccordException("Profile picture must be under 5MB.");

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase()))
            throw new AccordException("Profile picture must be a JPEG, PNG, GIF, or WebP image.");

        byte[] resized = resizeImage(file, 256, 256);
        String key = "pfp/" + userId + ".webp";     // deterministic — overwrites old pfp
        upload(key, resized, "image/webp");
        String url = publicUrl + "/" + bucket + "/" + key;

        visualsRepository.findById(userId).ifPresent(v -> {
            v.setDsPfpUrl(url);
            visualsRepository.save(v);
        });
        return url;
    }

    // =========================================================================
    // Banner
    // =========================================================================

    public String uploadBanner(UUID userId, MultipartFile file) throws IOException {
        if (file.isEmpty())
            throw new AccordException("File is empty.");
        if (file.getSize() > MAX_BANNER_SIZE)
            throw new AccordException("Banner must be under 8MB.");

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_BANNER_TYPES.contains(contentType.toLowerCase()))
            throw new AccordException("Banner must be JPEG, PNG, GIF, or WebP.");

        // Resize to a fixed canvas — raw 8MB PNGs are not stored as-is
        byte[] resized = resizeImage(file, BANNER_WIDTH, BANNER_HEIGHT);
        String key = "banners/" + userId + ".webp";  // deterministic — overwrites old banner
        upload(key, resized, "image/webp");
        String url = publicUrl + "/" + bucket + "/" + key;

        visualsRepository.findById(userId).ifPresent(v -> {
            v.setDsBannerUrl(url);
            visualsRepository.save(v);
        });
        return url;
    }

    // =========================================================================
    // Emoji
    // =========================================================================

    public String uploadEmoji(UUID serverId, UUID creatorId,
                              String name, MultipartFile file) throws IOException {
        if (file.isEmpty())
            throw new AccordException("File is empty.");
        if (file.getSize() > MAX_EMOJI_SIZE)
            throw new AccordException("Emoji must be under 256KB.");

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_EMOJI_TYPES.contains(contentType.toLowerCase()))
            throw new AccordException("Emoji must be PNG, GIF, or WebP.");

        if (!name.matches("[a-zA-Z0-9_]{2,50}"))
            throw new AccordException("Emoji name must be 2–50 alphanumeric characters or underscores.");

        if (emojiRepository.existsByIdServerAndDsName(serverId, name))
            throw new AccordException("An emoji with that name already exists in this server.");

        boolean animated = "image/gif".equalsIgnoreCase(contentType);
        byte[]  bytes    = animated ? file.getBytes() : resizeImage(file, 128, 128);
        String  ext      = animated ? ".gif" : ".png";
        String  key      = "emojis/" + serverId + "/" + name + ext;

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
            deleteFromS3(emoji.getDsUrl());
            emojiRepository.delete(emoji);
        });
    }

    // =========================================================================
    // Server message attachment
    // =========================================================================

    public String uploadAttachment(UUID messageId, MultipartFile file) throws IOException {
        if (file.isEmpty())
            throw new AccordException("File is empty.");
        if (file.getSize() > MAX_ATTACHMENT_SIZE)
            throw new AccordException("Attachment must be under 25MB.");

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_ATTACHMENT_TYPES.contains(contentType.toLowerCase()))
            throw new AccordException("File type not allowed.");

        // ── Read once ─────────────────────────────────────────────────────────
        byte[]   bytes            = file.getBytes();
        String   finalContentType = contentType;
        String   ext              = getExtension(file.getOriginalFilename());
        Integer  width            = null;
        Integer  height           = null;

        if (contentType.startsWith("image/") && !"image/gif".equalsIgnoreCase(contentType)) {
            BufferedImage img = readImage(bytes);
            width  = img.getWidth();
            height = img.getHeight();

            if (width > ATTACHMENT_IMAGE_MAX_DIM || height > ATTACHMENT_IMAGE_MAX_DIM) {
                img    = scaleToFit(img, ATTACHMENT_IMAGE_MAX_DIM);
                width  = img.getWidth();
                height = img.getHeight();
            }
            bytes            = toWebp(img);         // ← was toJpeg
            finalContentType = "image/webp";        // ← was image/jpeg
            ext              = ".webp";             // ← was .jpg
        }

        // ── Deduplication ─────────────────────────────────────────────────────
        String hash = sha256(bytes);
        Optional<MsAttachment> existing = msAttachmentRepository.findByDsSha256(hash);
        if (existing.isPresent()) {
            MsAttachment dup = MsAttachment.builder()
                    .idMessage(messageId)
                    .dsUrl(existing.get().getDsUrl())
                    .dsFilename(file.getOriginalFilename())
                    .dsMimeType(finalContentType)
                    .nrSizeBytes((long) bytes.length)
                    .nrWidth(width).nrHeight(height)
                    .dsSha256(hash)
                    .dtLastAccessed(ZonedDateTime.now())
                    .build();
            msAttachmentRepository.save(dup);
            return existing.get().getDsUrl();
        }

        String key = "attachments/" + messageId + "/" + UUID.randomUUID() + ext;
        upload(key, bytes, finalContentType);
        String url = publicUrl + "/" + bucket + "/" + key;

        msAttachmentRepository.save(MsAttachment.builder()
                .idMessage(messageId)
                .dsUrl(url)
                .dsFilename(file.getOriginalFilename())
                .dsMimeType(finalContentType)
                .nrSizeBytes((long) bytes.length)
                .nrWidth(width).nrHeight(height)
                .dsSha256(hash)
                .dtLastAccessed(ZonedDateTime.now())
                .build());
        return url;
    }

    // =========================================================================
    // DM attachment
    // =========================================================================

    // Called by VideoCompressor — same package, bridges the private upload()
    void uploadPackage(String key, byte[] data, String contentType) {
        upload(key, data, contentType);
    }

    // Called by VideoCompressor to fetch the raw video for FFmpeg
    byte[] downloadFromS3(String key) {
        return s3Client.getObjectAsBytes(
                software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .build()
        ).asByteArray();
    }

    public String uploadDmAttachment(UUID messageId, MultipartFile file) throws IOException {
        if (file.isEmpty())
            throw new AccordException("File is empty.");
        if (file.getSize() > MAX_ATTACHMENT_SIZE)
            throw new AccordException("Attachment must be under 25MB.");

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_ATTACHMENT_TYPES.contains(contentType.toLowerCase()))
            throw new AccordException("File type not allowed.");

        // ── Read once ─────────────────────────────────────────────────────────
        byte[]   bytes            = file.getBytes();
        String   finalContentType = contentType;
        String   ext              = getExtension(file.getOriginalFilename());
        Integer  width            = null;
        Integer  height           = null;

        if (contentType.startsWith("video/")) {
            // 1. Upload raw immediately so the user sees something
            String key = "dm-attachments/" + messageId + "/" + UUID.randomUUID() + ext;
            upload(key, bytes, contentType);
            String rawUrl = publicUrl + "/" + bucket + "/" + key;

            // 2. Save attachment row with raw URL
            DmAttachment saved = dmAttachmentRepository.save(DmAttachment.builder()
                    .idMessage(messageId)
                    .dsUrl(rawUrl)
                    .dsFilename(file.getOriginalFilename())
                    .dsMimeType(contentType)
                    .nrSizeBytes(file.getSize())
                    .dtLastAccessed(ZonedDateTime.now())
                    .build());

            // 3. Queue compression — updates the row and rebroadcasts when done
            videoCompressor.compressAsync(saved.getIdAttachment(), key, rawUrl);

            dmService.broadcastAttachmentUpdate(messageId);
            return rawUrl;
        }

        if (contentType.startsWith("image/") && !"image/gif".equalsIgnoreCase(contentType)) {
            BufferedImage img = readImage(bytes);
            width  = img.getWidth();
            height = img.getHeight();

            if (width > ATTACHMENT_IMAGE_MAX_DIM || height > ATTACHMENT_IMAGE_MAX_DIM) {
                img    = scaleToFit(img, ATTACHMENT_IMAGE_MAX_DIM);
                width  = img.getWidth();
                height = img.getHeight();
            }
            bytes            = toWebp(img);         // ← was toJpeg
            finalContentType = "image/webp";        // ← was image/jpeg
            ext              = ".webp";             // ← was .jpg
        }

        // ── Deduplication ─────────────────────────────────────────────────────
        String hash = sha256(bytes);
        Optional<DmAttachment> existing = dmAttachmentRepository.findByDsSha256(hash);
        if (existing.isPresent()) {
            DmAttachment dup = DmAttachment.builder()
                    .idMessage(messageId)
                    .dsUrl(existing.get().getDsUrl())
                    .dsFilename(file.getOriginalFilename())
                    .dsMimeType(finalContentType)
                    .nrSizeBytes((long) bytes.length)
                    .nrWidth(width).nrHeight(height)
                    .dsSha256(hash)
                    .dtLastAccessed(ZonedDateTime.now())
                    .build();
            dmAttachmentRepository.save(dup);
            dmService.broadcastAttachmentUpdate(messageId);
            return existing.get().getDsUrl();
        }

        String key = "dm-attachments/" + messageId + "/" + UUID.randomUUID() + ext;
        upload(key, bytes, finalContentType);
        String url = publicUrl + "/" + bucket + "/" + key;

        dmAttachmentRepository.save(DmAttachment.builder()
                .idMessage(messageId)
                .dsUrl(url)
                .dsFilename(file.getOriginalFilename())
                .dsMimeType(finalContentType)
                .nrSizeBytes((long) bytes.length)
                .nrWidth(width).nrHeight(height)
                .dsSha256(hash)
                .dtLastAccessed(ZonedDateTime.now())
                .build());

        dmService.broadcastAttachmentUpdate(messageId);
        return url;
    }

    // =========================================================================
    // S3 helpers
    // =========================================================================

    private void upload(String key, byte[] data, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .cacheControl("public, max-age=31536000, immutable")
                        .build(),
                RequestBody.fromBytes(data)
        );
    }

    // Streaming overload — kept for cases where we never touch the bytes (large non-image files)
    private void upload(String key, MultipartFile file, String contentType) throws IOException {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .contentLength(file.getSize())
                        .cacheControl("public, max-age=31536000, immutable")
                        .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize())
        );
    }

    public void deleteFromS3(String url) {
        String prefix = publicUrl + "/" + bucket + "/";
        if (!url.startsWith(prefix))
            throw new IllegalArgumentException("URL does not match expected pattern: " + url);
        String key = url.substring(prefix.length());
        s3Client.deleteObject(
                software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                        .bucket(bucket).key(key).build()
        );
    }

    // =========================================================================
    // Image helpers
    // =========================================================================

    /** Resize to an exact canvas — used for PFP and banner. */
    private byte[] resizeImage(MultipartFile file, int w, int h) throws IOException {
        BufferedImage src = ImageIO.read(file.getInputStream());
        if (src == null)
            throw new AccordException("Could not read image — file may be corrupt or unsupported.");
        BufferedImage dest = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);  // ARGB now
        Graphics2D g = dest.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return toWebp(dest);
    }

    /** Scale so the longest edge = maxDim, preserving aspect ratio. */
    private BufferedImage scaleToFit(BufferedImage src, int maxDim) {
        int w = src.getWidth(), h = src.getHeight();
        double scale = Math.min((double) maxDim / w, (double) maxDim / h);
        int nw = (int) (w * scale), nh = (int) (h * scale);
        BufferedImage dest = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);  // ARGB now
        Graphics2D g = dest.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dest;
    }

    private BufferedImage readImage(byte[] bytes) throws IOException {
        BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        if (img == null)
            throw new AccordException("Could not read image — file may be corrupt or unsupported.");
        return img;
    }

    private byte[] toWebp(BufferedImage img) throws IOException {
        // Ensure ARGB for WebP — it handles alpha natively
        if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage argb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = argb.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
            img = argb;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "webp", out);
        return out.toByteArray();
    }

    // =========================================================================
    // Misc helpers
    // =========================================================================

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.'));
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}