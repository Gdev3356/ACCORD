package com.main.accord.upload;

import com.main.accord.domain.dm.DmAttachmentRepository;
import com.main.accord.domain.dm.DmService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class VideoCompressor {

    private final DmAttachmentRepository dmAttachmentRepository;
    private final DmService              dmService;
    private final S3Client               s3Client;   // ← inject directly, no UploadService

    @Value("${supabase.storage.bucket}")
    private String bucket;

    @Value("${supabase.storage.public-url}")
    private String publicUrl;

    @Async
    public void compressAsync(UUID attachmentId, String originalKey, String rawUrl) {
        File inFile  = null;
        File outFile = null;
        try {
            byte[] rawBytes = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(originalKey).build()
            ).asByteArray();

            inFile  = File.createTempFile("accord_in_",  ".mp4");
            outFile = File.createTempFile("accord_out_", ".mp4");
            Files.write(inFile.toPath(), rawBytes);

            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-y",
                    "-i", inFile.getAbsolutePath(),
                    "-vcodec", "libx264",
                    "-crf", "28",
                    "-preset", "fast",
                    "-vf", "scale='min(1280,iw)':-2",
                    "-movflags", "+faststart",
                    "-acodec", "aac",
                    outFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            if (!p.waitFor(120, TimeUnit.SECONDS))
                throw new RuntimeException("FFmpeg timed out");

            byte[] compressed = Files.readAllBytes(outFile.toPath());
            String newKey     = originalKey.replace(".mp4", "_c.mp4");

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(newKey)
                            .contentType("video/mp4")
                            .cacheControl("public, max-age=31536000, immutable")
                            .build(),
                    RequestBody.fromBytes(compressed)
            );

            String newUrl = publicUrl + "/" + bucket + "/" + newKey;

            dmAttachmentRepository.findById(attachmentId).ifPresent(att -> {
                att.setDsUrl(newUrl);
                att.setNrSizeBytes((long) compressed.length);
                dmAttachmentRepository.save(att);
                dmService.broadcastAttachmentUpdate(att.getIdMessage());
            });

            s3Client.deleteObject(
                    DeleteObjectRequest.builder().bucket(bucket).key(originalKey).build()
            );

        } catch (Exception e) {
            // Raw file stays intact — compression failure is silent to the user
        } finally {
            if (inFile  != null) inFile.delete();
            if (outFile != null) outFile.delete();
        }
    }
}