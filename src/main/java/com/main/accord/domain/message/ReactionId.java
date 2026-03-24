package com.main.accord.domain.message;

import java.io.Serializable;
import java.util.UUID;

public record ReactionId(UUID idMessage, UUID idUser, String dsEmoji) implements Serializable {}