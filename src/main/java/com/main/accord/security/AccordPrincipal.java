package com.main.accord.security;

import java.security.Principal;
import java.util.UUID;

public record AccordPrincipal(UUID userId, String email, boolean isAdmin)
        implements Principal {
    @Override public String getName() { return userId.toString(); }
}