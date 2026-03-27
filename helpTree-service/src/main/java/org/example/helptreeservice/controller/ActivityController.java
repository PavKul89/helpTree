package org.example.helptreeservice.controller;

import org.example.helptreeservice.dto.activity.ActivityDto;
import org.example.helptreeservice.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping("/activities")
    public ResponseEntity<List<ActivityDto>> getUserActivities(
            @RequestParam(required = false, name = "userId") Long userId,
            @RequestParam(defaultValue = "50") int limit) {
        if (userId != null) {
            return ResponseEntity.ok(activityService.getUserActivities(userId, limit));
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/users/{userId}/activities")
    public ResponseEntity<List<ActivityDto>> getUserActivitiesById(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(activityService.getUserActivities(userId, limit));
    }
}
