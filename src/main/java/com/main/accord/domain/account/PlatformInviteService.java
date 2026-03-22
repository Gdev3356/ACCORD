package com.main.accord.domain.account;

import com.main.accord.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlatformInviteService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int    LENGTH   = 12;

    private final PlatformInviteRepository platformInviteRepository;
    private final AccountRepository        accountRepository;

    @Transactional
    public PlatformInvite createInvite(UUID creatorId, Integer expiresInDays) {
        accountRepository.findById(creatorId)
                .orElseThrow(() -> new NotFoundException("Creator not found."));

        OffsetDateTime expires = expiresInDays != null
                ? OffsetDateTime.now().plusDays(expiresInDays)
                : null;

        return platformInviteRepository.save(
                PlatformInvite.builder()
                        .dsCode(generateCode())
                        .idCreator(creatorId)
                        .dtExpires(expires)
                        .build()
        );
    }

    public List<PlatformInvite> getMyInvites(UUID creatorId) {
        return platformInviteRepository.findByIdCreatorOrderByDtCreatedDesc(creatorId);
    }

    private String generateCode() {
        SecureRandom rng = new SecureRandom();
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(rng.nextInt(ALPHABET.length())));
        }
        String code = sb.toString();
        return platformInviteRepository.findByDsCode(code).isPresent()
                ? generateCode()
                : code;
    }
}