package com.main.accord.domain.game;

import com.main.accord.common.AccordException;
import com.main.accord.common.NotFoundException;
import com.main.accord.domain.account.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PlayerSettingsService {

    private final PlayerSettingsRepository settingsRepository;
    private final AccountRepository accountRepository;
    private final GmGameRepository gameRepository;  // ← Add this to look up game by slug

    // ── Validators registry ───────────────────────────────────────────────────

    public interface GameSettingsValidator {
        String gameSlug();
        void validate(Map<String, Object> patch);
    }

    @org.springframework.stereotype.Component
    static class AbSettingsValidator implements GameSettingsValidator {

        private static final Set<String> ALLOWED_KEYS = Set.of(
                "audioEnabled",
                "volume"
        );

        @Override public String gameSlug() { return "ab"; }

        @Override
        public void validate(Map<String, Object> patch) {
            for (String key : patch.keySet()) {
                if (!ALLOWED_KEYS.contains(key)) {
                    throw new AccordException("Unknown settings key: " + key);
                }
            }
            if (patch.containsKey("audioEnabled") &&
                    !(patch.get("audioEnabled") instanceof Boolean)) {
                throw new AccordException("'audioEnabled' must be a boolean.");
            }
            if (patch.containsKey("volume")) {
                Object v = patch.get("volume");
                if (!(v instanceof Number num) || num.doubleValue() < 0 || num.doubleValue() > 1) {
                    throw new AccordException("'volume' must be a number between 0 and 1.");
                }
            }
        }
    }

    private final java.util.List<GameSettingsValidator> validators;

    private GameSettingsValidator validatorFor(String slug) {
        return validators.stream()
                .filter(v -> v.gameSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No settings defined for game: " + slug));
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Map<String, Object> getSettings(UUID userId, String gameSlug) {
        // Look up the game to get its UUID
        GmGame game = gameRepository.findByDsSlug(gameSlug)
                .orElseThrow(() -> new NotFoundException("Game not found: " + gameSlug));

        return settingsRepository.findByIdIdUserAndIdGame(userId, game.getIdGame())
                .map(PlayerSettings::getJsSettings)
                .orElse(Map.of());
    }

    @Transactional
    public Map<String, Object> patchSettings(UUID userId, String gameSlug,
                                             Map<String, Object> patch) {
        if (patch == null || patch.isEmpty()) {
            throw new AccordException("Settings patch must not be empty.");
        }

        // 1. Validate keys + value types for this game
        validatorFor(gameSlug).validate(patch);

        // 2. Verify account exists
        accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));

        // 3. Look up game to get UUID
        GmGame game = gameRepository.findByDsSlug(gameSlug)
                .orElseThrow(() -> new NotFoundException("Game not found: " + gameSlug));

        // 4. Upsert with merge semantics (using UUID)
        PlayerSettings settings = settingsRepository
                .findByIdIdUserAndIdGame(userId, game.getIdGame())
                .orElseGet(() -> PlayerSettings.builder()
                        .idUser(userId)
                        .idGame(game.getIdGame())
                        .jsSettings(new HashMap<>())
                        .build());

        settings.getJsSettings().putAll(patch);
        settings.setDtUpdated(OffsetDateTime.now());

        return settingsRepository.save(settings).getJsSettings();
    }
}