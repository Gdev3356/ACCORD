package com.main.accord.domain.server;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class ServerSummaryDto {
    private UUID   idServer;
    private String dsName;
    private String dsIconUrl;
    private String dsDescription;
    private long   nrUnread;
}