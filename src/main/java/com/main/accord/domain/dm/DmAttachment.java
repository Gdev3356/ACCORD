package com.main.accord.domain.dm;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "DM_ATTACHMENT")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DmAttachment {

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_ATTACHMENT")
    private UUID idAttachment;

    @Column(name = "ID_MESSAGE", nullable = false)
    private UUID idMessage;

    @Column(name = "DS_URL", nullable = false)
    private String dsUrl;

    @Column(name = "DS_FILENAME", length = 255)
    private String dsFilename;

    @Column(name = "DS_MIME_TYPE", length = 100)
    private String dsMimeType;

    @Column(name = "NR_SIZE_BYTES")
    private Long nrSizeBytes;

    @Column(name = "NR_WIDTH")
    private Integer nrWidth;

    @Column(name = "NR_HEIGHT")
    private Integer nrHeight;
}