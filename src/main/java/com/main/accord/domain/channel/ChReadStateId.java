package com.main.accord.domain.channel;

import java.io.Serializable;
import java.util.UUID;

public record ChReadStateId(UUID idChannel, UUID idUser) implements Serializable {}