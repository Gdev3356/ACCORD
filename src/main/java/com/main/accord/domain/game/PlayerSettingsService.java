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
    private final GmGameRepository         gameRepository;
    private final AccountRepository         accountRepository;

    // ── Validators registry ───────────────────────────────────────────────────

    /**
     * Per-game validator: checks allowed keys and value types.
     * Add a new implementation for each game slug.
     */
    public interface GameSettingsValidator {
        String gameSlug();
        void validate(Map<String, Object> patch);   // throws AccordException on bad input
    }

    // Angry Birds — only known keys are accepted; values are type-checked.
    @org.springframework.stereotype.Component
    static class AbSettingsValidator implements GameSettingsValidator {

        private static final Set<String> ALLOWED_KEYS = Set.of(
                "audioEnabled",
                "volume"        // reserved for future use
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

    // ── Constructor injection of all validators ───────────────────────────────

    private final java.util.List<GameSettingsValidator> validators;

    private GameSettingsValidator validatorFor(String slug) {
        return validators.stream()
                .filter(v -> v.gameSlug().equals(slug))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("No settings defined for game: " + slug));
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public Map<String, Object> getSettings(UUID userId, String gameSlug) {
        return settingsRepository
                .findByIdIdUserAndGameDsSlug(userId, gameSlug)
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

        // 2. Resolve game + account (both must exist)
        GmGame game = gameRepository.findByDsSlug(gameSlug)
                .orElseThrow(() -> new NotFoundException("Game not found: " + gameSlug));

        accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found."));

        // 3. Upsert with merge semantics
        PlayerSettingsId pk = new PlayerSettingsId(userId, game.getIdGame());

        PlayerSettings settings = settingsRepository.findById(pk)
                .orElseGet(() -> PlayerSettings.builder()
                        .id(pk)
                        .account(accountRepository.getReferenceById(userId))
                        .game(game)
                        .jsSettings(new HashMap<>())
                        .build());

        settings.getJsSettings().putAll(patch);   // merge, not replace
        settings.setDtUpdated(OffsetDateTime.now());

        return settingsRepository.save(settings).getJsSettings();
    }
}