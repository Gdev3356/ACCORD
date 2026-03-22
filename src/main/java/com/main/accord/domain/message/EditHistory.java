package com.main.accord.domain.message;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "MS_EDIT_HISTORY")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EditHistory {

    @Id @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID_EDIT")
    private UUID idEdit;

    @Column(name = "ID_MESSAGE", nullable = false)
    private UUID idMessage;

    @Column(name = "DS_CONTENT", columnDefinition = "TEXT", nullable = false)
    private String dsContent;

    @CreationTimestamp
    @Column(name = "DT_EDITED")
    private OffsetDateTime dtEdited;
}
