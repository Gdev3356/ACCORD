package com.main.accord.domain.message;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "MS_ATTACHMENT")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MsAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_attachment")
    private UUID idAttachment;

    @Column(name = "id_message", nullable = false)
    private UUID idMessage;

    @Column(name = "ds_url", nullable = false)
    private String dsUrl;

    @Column(name = "ds_filename")
    private String dsFilename;

    @Column(name = "ds_mime_type")
    private String dsMimeType;

    @Column(name = "nr_size_bytes")
    private Long nrSizeBytes;

    @Column(name = "nr_width")
    private Integer nrWidth;

    @Column(name = "nr_height")
    private Integer nrHeight;

    @Column(name = "dt_last_accessed")
    private ZonedDateTime dtLastAccessed = ZonedDateTime.now();

    @Column(name = "ds_sha256")
    private String dsSha256;
}