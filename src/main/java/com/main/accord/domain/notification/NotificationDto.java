package com.main.accord.domain.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class NotificationDto {
    @JsonProperty("idNotif")
    private UUID id;

    @JsonProperty("tpNotif")
    private NotifType type;

    @JsonProperty("dsTitle")
    private String title;

    @JsonProperty("dsBody")
    private String body;

    @JsonProperty("jsPayload")
    private Map<String, Object> payload;

    @JsonProperty("stRead")
    private boolean read;

    @JsonProperty("dtCreated")
    private OffsetDateTime createdAt;
}