package org.example.helptreeservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.activity.ActivityDto;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Activity", description = "Публичная лента активности пользователя")
public class ActivityController {

    private final ActivityService activityService;
    private final PaginationConfig paginationConfig;

    /**
     * Public activity feed. {@code limit} is kept as a legacy alias for
     * {@code size}; both are clamped to the configured maximum.
     */
    @GetMapping("/activities")
    @Operation(summary = "Лента активности",
            description = "Без userId возвращает пустую страницу. limit — legacy alias для size; "
                    + "size ограничивается сервером. Ответ — PageResponse.")
    @SecurityRequirements
    public ResponseEntity<PageResponse<ActivityDto>> getUserActivities(
            @RequestParam(required = false, name = "userId") Long userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, name = "limit") Integer limit,
            @RequestParam(required = false, defaultValue = "ALL") String type) {

        int resolvedPage = resolvePage(page);
        int resolvedSize = resolveSize(size, limit);
        if (userId == null) {
            return ResponseEntity.ok(PageResponse.empty(resolvedPage, resolvedSize));
        }
        return ResponseEntity.ok(
                activityService.getUserActivities(userId, type, resolvedPage, resolvedSize));
    }

    @GetMapping("/users/{userId}/activities")
    @Operation(summary = "Лента активности конкретного пользователя (постранично)")
    @SecurityRequirements
    public ResponseEntity<PageResponse<ActivityDto>> getUserActivitiesById(
            @PathVariable Long userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false, name = "limit") Integer limit,
            @RequestParam(required = false, defaultValue = "ALL") String type) {

        return ResponseEntity.ok(activityService.getUserActivities(
                userId, type, resolvePage(page), resolveSize(size, limit)));
    }

    private int resolvePage(Integer page) {
        return page != null && page >= 0 ? page : 0;
    }

    private int resolveSize(Integer size, Integer limit) {
        Integer requested = size != null ? size : limit;
        int value = requested != null ? requested : paginationConfig.getDefaultPageSize();
        if (value < 1) {
            return 1;
        }
        return Math.min(value, paginationConfig.getMaxPageSize());
    }
}
