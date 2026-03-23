package com.main.accord.domain.server;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SvEmojiRepository extends JpaRepository<SvEmoji, UUID> {
    List<SvEmoji> findByIdServer(UUID idServer);
    boolean existsByIdServerAndDsName(UUID idServer, String dsName);
}