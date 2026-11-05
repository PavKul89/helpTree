package org.example.helptreeservice.controller;

import org.example.helptreeservice.config.PaginationConfig;
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
    private final PaginationConfig paginationConfig;

    @GetMapping("/activities")
    public ResponseEntity<List<ActivityDto>> getUserActivities(
            @RequestParam(required = false, name = "userId") Long userId,
            @RequestParam(defaultValue = "50") int limit) {
        if (userId != null) {
            return ResponseEntity.ok(activityService.getUserActivities(userId, resolveLimit(limit)));
        }
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/users/{userId}/activities")
    public ResponseEntity<List<ActivityDto>> getUserActivitiesById(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(activityService.getUserActivities(userId, resolveLimit(limit)));
    }

    private int resolveLimit(int limit) {
        if (limit < 1) {
            return 1;
        }
        return Math.min(limit, paginationConfig.getMaxPageSize());
    }
}
