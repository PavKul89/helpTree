package org.example.helptreeservice.controller;

import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.dto.notifications.MarkAllReadResponse;
import org.example.helptreeservice.dto.notifications.NotificationDto;
import org.example.helptreeservice.dto.notifications.NotificationPreferencesResponse;
import org.example.helptreeservice.dto.notifications.UnreadCountResponse;
import org.example.helptreeservice.dto.notifications.UpdateNotificationPreferencesRequest;
import org.example.helptreeservice.enums.NotificationChannel;
import org.example.helptreeservice.enums.NotificationType;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock private NotificationService notificationService;
    @Mock private AuthorizationService authService;

    private PaginationConfig paginationConfig;
    private NotificationController controller;

    @BeforeEach
    void setUp() {
        paginationConfig = new PaginationConfig();
        paginationConfig.setDefaultPageSize(20);
        paginationConfig.setMaxPageSize(100);
        controller = new NotificationController(notificationService, authService, paginationConfig);
    }

    @Test
    void everyEndpointRequiresAnAuthenticatedUser() {
        when(authService.getCurrentUser()).thenReturn(null);

        assertThrows(UnauthorizedException.class,
                () -> controller.getNotifications(0, 20, false));
        assertThrows(UnauthorizedException.class, controller::getUnreadCount);
        assertThrows(UnauthorizedException.class, () -> controller.markRead(1L));
        assertThrows(UnauthorizedException.class, controller::markAllRead);
        assertThrows(UnauthorizedException.class, controller::getPreferences);
        assertThrows(UnauthorizedException.class,
                () -> controller.updatePreferences(new UpdateNotificationPreferencesRequest()));
    }

    @Test
    void listUsesTheCurrentUserAndClampsThePageSize() {
        when(authService.getCurrentUser()).thenReturn(user(7L));
        Page<NotificationDto> page = new PageImpl<>(
                List.of(NotificationDto.builder().id(1L).userId(7L).build()),
                PageRequest.of(0, 100), 1);
        when(notificationService.getNotifications(eq(7L), any(Pageable.class), eq(false)))
                .thenReturn(page);

        ResponseEntity<PageResponse<NotificationDto>> response =
                controller.getNotifications(0, 500, false);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getContent().size());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).getNotifications(eq(7L), captor.capture(), eq(false));
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void listFallsBackToTheConfiguredDefaultSize() {
        when(authService.getCurrentUser()).thenReturn(user(7L));
        when(notificationService.getNotifications(eq(7L), any(Pageable.class), eq(true)))
                .thenReturn(Page.<NotificationDto>empty());

        controller.getNotifications(0, null, true);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationService).getNotifications(eq(7L), captor.capture(), eq(true));
        assertEquals(20, captor.getValue().getPageSize());
    }

    @Test
    void negativePageAndNonPositiveSizeAreRejected() {
        when(authService.getCurrentUser()).thenReturn(user(7L));

        assertThrows(BadRequestException.class, () -> controller.getNotifications(-1, 20, false));
        assertThrows(BadRequestException.class, () -> controller.getNotifications(0, 0, false));
    }

    @Test
    void unreadCountAndReadEndpointsDelegateToTheCurrentUser() {
        when(authService.getCurrentUser()).thenReturn(user(7L));
        when(notificationService.getUnreadCount(7L))
                .thenReturn(new UnreadCountResponse(4));
        when(notificationService.markRead(7L, 21L))
                .thenReturn(new UnreadCountResponse(3));
        when(notificationService.markAllRead(7L))
                .thenReturn(new MarkAllReadResponse(3, 0));

        assertEquals(4, controller.getUnreadCount().getBody().getUnreadCount());
        assertEquals(3, controller.markRead(21L).getBody().getUnreadCount());
        assertEquals(3, controller.markAllRead().getBody().getUpdated());
    }

    @Test
    void preferencesEndpointsDelegateToTheCurrentUser() {
        when(authService.getCurrentUser()).thenReturn(user(7L));
        NotificationPreferencesResponse matrix = NotificationPreferencesResponse.builder()
                .preferences(List.of(org.example.helptreeservice.dto.notifications.NotificationPreferenceDto
                        .builder()
                        .type(NotificationType.NEW_REVIEW)
                        .channel(NotificationChannel.IN_APP)
                        .enabled(true)
                        .build()))
                .types(List.of(NotificationType.values()))
                .channels(List.of(NotificationChannel.values()))
                .build();
        when(notificationService.getPreferences(7L)).thenReturn(matrix);
        when(notificationService.updatePreferences(eq(7L), any())).thenReturn(matrix);

        UpdateNotificationPreferencesRequest request = new UpdateNotificationPreferencesRequest();
        request.setPreferences(List.of());

        assertEquals(matrix, controller.getPreferences().getBody());
        assertEquals(matrix, controller.updatePreferences(request).getBody());
        verify(notificationService).updatePreferences(eq(7L), any());
    }

    private AuthorizationService.UserContext user(Long userId) {
        return new AuthorizationService.UserContext(userId, "USER", "user@test.local");
    }
}
