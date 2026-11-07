package org.example.helptreeservice.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.dto.notifications.MarkAllReadResponse;
import org.example.helptreeservice.dto.notifications.NotificationDto;
import org.example.helptreeservice.dto.notifications.NotificationPreferencesResponse;
import org.example.helptreeservice.dto.notifications.UnreadCountResponse;
import org.example.helptreeservice.dto.notifications.UpdateNotificationPreferencesRequest;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.notification.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Authenticated self-service notification API. The current user is always taken
 * from the security context; no endpoint accepts a user id.
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final AuthorizationService authService;
    private final PaginationConfig paginationConfig;

    @GetMapping
    public ResponseEntity<PageResponse<NotificationDto>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "false") boolean unreadOnly) {
        Long userId = requireUserId();
        int resolvedSize = size == null ? paginationConfig.getDefaultPageSize() : size;
        Page<NotificationDto> notifications = notificationService.getNotifications(
                userId, resolvePageable(page, resolvedSize), unreadOnly);
        return ResponseEntity.ok(PageResponse.from(notifications));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount() {
        return ResponseEntity.ok(notificationService.getUnreadCount(requireUserId()));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<UnreadCountResponse> markRead(@PathVariable Long notificationId) {
        return ResponseEntity.ok(notificationService.markRead(requireUserId(), notificationId));
    }

    @PostMapping("/read-all")
    public ResponseEntity<MarkAllReadResponse> markAllRead() {
        return ResponseEntity.ok(notificationService.markAllRead(requireUserId()));
    }

    @GetMapping("/preferences")
    public ResponseEntity<NotificationPreferencesResponse> getPreferences() {
        return ResponseEntity.ok(notificationService.getPreferences(requireUserId()));
    }

    @PutMapping("/preferences")
    public ResponseEntity<NotificationPreferencesResponse> updatePreferences(
            @Valid @RequestBody UpdateNotificationPreferencesRequest request) {
        return ResponseEntity.ok(
                notificationService.updatePreferences(requireUserId(), request));
    }

    private Long requireUserId() {
        AuthorizationService.UserContext user = authService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }
        return user.getUserId();
    }

    private org.springframework.data.domain.Pageable resolvePageable(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Параметр page не может быть отрицательным");
        }
        if (size < 1) {
            throw new BadRequestException("Параметр size должен быть положительным");
        }
        return PageRequest.of(page, Math.min(size, paginationConfig.getMaxPageSize()));
    }
}
