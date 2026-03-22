package com.main.accord.websocket;

import com.main.accord.security.AccordPrincipal;
import java.security.Principal;

public record StompUser(AccordPrincipal principal) implements Principal {
    @Override public String getName() { return principal.userId().toString(); }
}