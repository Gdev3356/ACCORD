package com.main.accord.domain.game;

import com.main.accord.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}/achievements")
@RequiredArgsConstructor
public class UserAchievementController {

    private final GmPlayerAchievementRepository playerAchievementRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UnlockedAchievement>>> getAchievements(
            @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.ok(
                playerAchievementRepository.findUnlockedByUser(userId)
        ));
    }
}