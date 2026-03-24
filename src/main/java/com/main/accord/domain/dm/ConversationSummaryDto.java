package com.main.accord.domain.dm;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data @Builder
public class ConversationSummaryDto {
    private UUID    idConversation;
    private boolean stGroup;
    private String  dsName;

    // Null for group DMs
    private UUID    otherId;
    private String  otherDisplayName;
    private String  otherPfpUrl;
    private String  otherPresence;
    private boolean isFriend;
    private Integer nrUnread;
}