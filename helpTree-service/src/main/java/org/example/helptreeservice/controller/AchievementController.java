package org.example.helptreeservice.controller;

import org.example.helptreeservice.dto.achievement.AchievementDto;
import org.example.helptreeservice.service.AchievementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;

    @GetMapping("/achievements")
    public ResponseEntity<List<AchievementDto>> getAllAchievements(
            @RequestParam(required = false, name = "userId") Long userId) {
        if (userId != null) {
            return ResponseEntity.ok(achievementService.getAllAchievementTypesWithProgress(userId));
        }
        return ResponseEntity.ok(achievementService.getAllAchievementTypes());
    }

    @GetMapping("/users/{userId}/achievements")
    public ResponseEntity<List<AchievementDto>> getUserAchievements(@PathVariable Long userId) {
        return ResponseEntity.ok(achievementService.getUserAchievements(userId));
    }
}
