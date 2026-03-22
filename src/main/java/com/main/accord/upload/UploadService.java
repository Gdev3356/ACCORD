package com.main.accord.upload;

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
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadService {

    private final S3Client s3Client;

    @Value("${supabase.storage.bucket}")
    private String bucket;

    @Value("${supabase.storage.public-url}")
    private String publicUrl;

    /**
     * Upload a profile picture.
     * Resizes to 256×256 JPEG before uploading (poor man's Sharp).
     */
    public String uploadPfp(UUID userId, MultipartFile file) throws IOException {
        byte[] resized = resizeImage(file, 256, 256);
        String key     = "pfp/" + userId + ".jpg";
        upload(key, resized, "image/jpeg");
        return publicUrl + "/" + bucket + "/" + key;
    }

    /**
     * Upload a message attachment — no resize, straight through.
     */
    public String uploadAttachment(UUID messageId, MultipartFile file) throws IOException {
        String ext = getExtension(file.getOriginalFilename());
        String key = "attachments/" + messageId + "/" + UUID.randomUUID() + ext;
        upload(key, file.getBytes(), file.getContentType());
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
        BufferedImage src  = ImageIO.read(file.getInputStream());
        BufferedImage dest = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        dest.createGraphics().drawImage(
                src.getScaledInstance(w, h, Image.SCALE_SMOOTH), 0, 0, null
        );
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(dest, "jpg", out);
        return out.toByteArray();
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf('.'));
    }
}