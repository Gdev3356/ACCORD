package com.main.accord.websocket;

import com.main.accord.domain.account.PresenceStatus;
import com.main.accord.domain.account.AccountService.PresenceDto;
import java.util.List;
import java.util.UUID;

public interface PresenceBroadcastService {
    void setPresence(UUID userId, PresenceStatus status);
    void setPresenceAuto(UUID userId, PresenceStatus status);
    void userConnected(UUID userId);
    void userDisconnected(UUID userId);
    List<PresenceDto> getRelevantPresences(UUID userId);
    void invalidateCache(UUID userId);
}