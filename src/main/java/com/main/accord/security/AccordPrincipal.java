package com.main.accord.security;

import java.util.UUID;

public record AccordPrincipal(
        UUID   userId,
        String email
) {}