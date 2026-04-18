package com.main.accord.domain.notification;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class NotificationDto {
    private UUID                 id;
    private NotifType            type;
    private String               title;
    private String               body;
    private Map<String, Object>  payload;
    private boolean              read;
    private OffsetDateTime       createdAt;
}