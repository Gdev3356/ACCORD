package com.main.accord.domain.dm;

import java.io.Serializable;
import java.util.UUID;

public record DmReadStateId(UUID idConversation, UUID idUser) implements Serializable {}