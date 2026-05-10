package com.main.accord.websocket;

import com.main.accord.security.AccordPrincipal;
import java.security.Principal;

public record StompUser(Principal principal) implements Principal {
    @Override
    public String getName() {
        // Must match what you pass to convertAndSendToUser()
        if (principal instanceof AccordPrincipal ap) {
            return ap.userId().toString();
        }
        return principal.getName();
    }
}