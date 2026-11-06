package org.example.helptreeservice.controller;

import org.example.helptreeservice.dto.achievement.AchievementDto;
import org.example.helptreeservice.service.AchievementService;
import org.example.helptreeservice.service.PrivacyPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AchievementController {

    private final AchievementService achievementService;
    private final PrivacyPolicy privacyPolicy;

    /**
     * Public achievement catalogue. Passing {@code userId} used to expose that
     * user's private progress, so the progress projection is now reserved for
     * the owner and admins; everybody else receives only the achievements the
     * user has actually earned (the same facts that are public in the activity
     * feed).
     */
    @GetMapping("/achievements")
    public ResponseEntity<List<AchievementDto>> getAllAchievements(
            @RequestParam(required = false, name = "userId") Long userId) {
        if (userId == null) {
            return ResponseEntity.ok(achievementService.getAllAchievementTypes());
        }
        if (!privacyPolicy.canViewPrivateActivity(userId)) {
            return ResponseEntity.ok(achievementService.getUserAchievements(userId));
        }
        return ResponseEntity.ok(achievementService.getAllAchievementTypesWithProgress(userId));
    }

    @GetMapping("/users/{userId}/achievements")
    public ResponseEntity<List<AchievementDto>> getUserAchievements(@PathVariable Long userId) {
        return ResponseEntity.ok(achievementService.getUserAchievements(userId));
    }
}
