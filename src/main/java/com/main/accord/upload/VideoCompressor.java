package com.main.accord.upload;

import com.main.accord.domain.dm.DmAttachmentRepository;
import com.main.accord.domain.dm.DmService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class VideoCompressor {

    private final DmAttachmentRepository dmAttachmentRepository;
    private final UploadService          uploadService;
    private final DmService              dmService;

    @Value("${supabase.storage.bucket}")
    private String bucket;

    @Value("${supabase.storage.public-url}")
    private String publicUrl;

    @Async
    public void compressAsync(UUID attachmentId, String originalKey, String rawUrl) {
        File inFile  = null;
        File outFile = null;
        try {
            // Fetch raw bytes from S3 to compress
            byte[] rawBytes = uploadService.downloadFromS3(originalKey);

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
            uploadService.uploadPackage(newKey, compressed, "video/mp4");
            String newUrl = publicUrl + "/" + bucket + "/" + newKey;

            dmAttachmentRepository.findById(attachmentId).ifPresent(att -> {
                att.setDsUrl(newUrl);
                att.setNrSizeBytes((long) compressed.length);
                dmAttachmentRepository.save(att);
                dmService.broadcastAttachmentUpdate(att.getIdMessage());
            });

            uploadService.deleteFromS3(rawUrl);

        } catch (Exception e) {
            // Raw file stays intact — compression failure is silent to the user
        } finally {
            if (inFile  != null) inFile.delete();
            if (outFile != null) outFile.delete();
        }
    }
}