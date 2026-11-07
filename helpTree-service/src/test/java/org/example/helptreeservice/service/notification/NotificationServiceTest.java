package org.example.helptreeservice.service.notification;

import org.example.helptreeservice.config.NotificationProperties;
import org.example.helptreeservice.dto.notifications.MarkAllReadResponse;
import org.example.helptreeservice.dto.notifications.NotificationDto;
import org.example.helptreeservice.dto.notifications.NotificationPreferenceDto;
import org.example.helptreeservice.dto.notifications.NotificationPreferencesResponse;
import org.example.helptreeservice.dto.notifications.UnreadCountResponse;
import org.example.helptreeservice.dto.notifications.UpdateNotificationPreferencesRequest;
import org.example.helptreeservice.entity.Notification;
import org.example.helptreeservice.entity.OutboxEvent;
import org.example.helptreeservice.enums.NotificationChannel;
import org.example.helptreeservice.enums.NotificationType;
import org.example.helptreeservice.enums.OutboxStatus;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.NotificationPreferenceRepository;
import org.example.helptreeservice.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationPreferenceRepository preferenceRepository;
    @Mock private NotificationRealtimeService realtimeService;
    @Mock private NotificationSenderRegistry senderRegistry;
    @Mock private OutboxService outboxService;
    @Mock private OutboxDispatcher outboxDispatcher;

    private NotificationProperties properties;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        service = new NotificationService(
                notificationRepository,
                preferenceRepository,
                realtimeService,
                senderRegistry,
                outboxService,
                outboxDispatcher,
                properties);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void notifyWritesCanonicalRowAndDispatchesOnlyAfterCommit() {
        NotificationCommand command = NotificationCommand.of(
                7L, NotificationType.NEW_REVIEW, "Новый отзыв", "Вам оставили отзыв.",
                Map.of("reviewId", 11L), "review:11");

        when(notificationRepository.insertIfAbsent(
                eq(7L), eq("NEW_REVIEW"), anyString(), anyString(), anyString(), eq("review:11"), any()))
                .thenReturn(1);
        Notification saved = notification(21L, 7L, NotificationType.NEW_REVIEW);
        when(notificationRepository.findByUserIdAndDedupeKey(7L, "review:11"))
                .thenReturn(Optional.of(saved));
        when(preferenceRepository.findByUserIdAndTypeAndChannel(
                7L, NotificationType.NEW_REVIEW, NotificationChannel.IN_APP))
                .thenReturn(Optional.empty());
        when(preferenceRepository.findByUserIdAndTypeAndChannel(
                7L, NotificationType.NEW_REVIEW, NotificationChannel.EMAIL))
                .thenReturn(Optional.empty());
        when(preferenceRepository.findByUserIdAndTypeAndChannel(
                7L, NotificationType.NEW_REVIEW, NotificationChannel.TELEGRAM))
                .thenReturn(Optional.empty());
        TransactionSynchronizationManager.initSynchronization();

        NotificationDto dto = service.notify(command);

        assertEquals(21L, dto.getId());
        assertEquals(7L, dto.getUserId());
        // Nothing must leave the transaction until the commit happens.
        verifyNoInteractions(realtimeService);
        verifyNoInteractions(outboxDispatcher);

        fireAfterCommit();

        verify(realtimeService).push(eq(7L), any(NotificationDto.class));
        // Email and Telegram are disabled by default, so no outbox row exists.
        verify(outboxService, never()).enqueue(any());
    }

    @Test
    void repeatedDedupeKeyReturnsExistingRowWithoutSecondDelivery() {        NotificationCommand command = NotificationCommand.of(
                7L, NotificationType.NEW_REVIEW, "Новый отзыв", "Вам оставили отзыв.",
                Map.of(), "review:11");

        when(notificationRepository.insertIfAbsent(
                eq(7L), eq("NEW_REVIEW"), anyString(), anyString(), anyString(), eq("review:11"), any()))
                .thenReturn(0);
        when(notificationRepository.findByUserIdAndDedupeKey(7L, "review:11"))
                .thenReturn(Optional.of(notification(21L, 7L, NotificationType.NEW_REVIEW)));

        NotificationDto dto = service.notify(command);

        assertEquals(21L, dto.getId());
        verifyNoInteractions(realtimeService);
        verifyNoInteractions(outboxDispatcher);
        verify(outboxService, never()).enqueue(any());
    }

    @Test
    void payloadWithTimestampsIsSerialisedAsIsoStrings() {
        LocalDateTime blockedUntil = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", 7L);
        payload.put("blockedUntil", blockedUntil);
        NotificationCommand command = NotificationCommand.of(
                7L, NotificationType.ACCOUNT_BLOCKED, "Аккаунт заблокирован",
                "Ваш аккаунт заблокирован.", payload,
                "account:blocked:7:2026-01-02T03:04:05");

        when(notificationRepository.insertIfAbsent(
                eq(7L), eq("ACCOUNT_BLOCKED"), anyString(), anyString(), anyString(),
                eq("account:blocked:7:2026-01-02T03:04:05"), any()))
                .thenReturn(1);
        when(notificationRepository.findByUserIdAndDedupeKey(
                7L, "account:blocked:7:2026-01-02T03:04:05"))
                .thenReturn(Optional.of(notification(21L, 7L, NotificationType.ACCOUNT_BLOCKED)));

        service.notify(command);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(notificationRepository).insertIfAbsent(
                eq(7L), eq("ACCOUNT_BLOCKED"), anyString(), anyString(), json.capture(),
                eq("account:blocked:7:2026-01-02T03:04:05"), any());
        assertTrue(json.getValue().contains("\"blockedUntil\":\"2026-01-02T03:04:05\""));
    }

    @Test
    void inAppPreferenceSuppressesPushButKeepsTheRow() {
        NotificationCommand command = NotificationCommand.of(
                7L, NotificationType.COINS_EARNED, "Начисление", "+10 HelpCoin",
                Map.of(), "wallet:1");

        when(notificationRepository.insertIfAbsent(
                anyLong(), eq("COINS_EARNED"), anyString(), anyString(), anyString(), eq("wallet:1"), any()))
                .thenReturn(1);
        when(notificationRepository.findByUserIdAndDedupeKey(7L, "wallet:1"))
                .thenReturn(Optional.of(notification(21L, 7L, NotificationType.COINS_EARNED)));
        when(preferenceRepository.findByUserIdAndTypeAndChannel(
                7L, NotificationType.COINS_EARNED, NotificationChannel.IN_APP))
                .thenReturn(Optional.of(preference(NotificationType.COINS_EARNED,
                        NotificationChannel.IN_APP, false)));
        when(preferenceRepository.findByUserIdAndTypeAndChannel(
                7L, NotificationType.COINS_EARNED, NotificationChannel.EMAIL))
                .thenReturn(Optional.empty());
        when(preferenceRepository.findByUserIdAndTypeAndChannel(
                7L, NotificationType.COINS_EARNED, NotificationChannel.TELEGRAM))
                .thenReturn(Optional.empty());
        TransactionSynchronizationManager.initSynchronization();
        NotificationDto dto = service.notify(command);
        fireAfterCommit();

        assertEquals(21L, dto.getId());
        verify(realtimeService, never()).push(anyLong(), any());
    }

    @Test
    void enabledTransportEnqueuesOneDeliveryEventAndDispatchesItAfterCommit() {
        NotificationCommand command = NotificationCommand.of(
                7L, NotificationType.HELP_ACCEPTED, "Отклик принят", "Вас выбрали.",
                Map.of(), "help:42:accepted:helper");

        when(notificationRepository.insertIfAbsent(
                anyLong(), eq("HELP_ACCEPTED"), anyString(), anyString(), anyString(),
                eq("help:42:accepted:helper"), any()))
                .thenReturn(1);
        when(notificationRepository.findByUserIdAndDedupeKey(7L, "help:42:accepted:helper"))
                .thenReturn(Optional.of(notification(21L, 7L, NotificationType.HELP_ACCEPTED)));
        when(preferenceRepository.findByUserIdAndTypeAndChannel(
                7L, NotificationType.HELP_ACCEPTED, NotificationChannel.IN_APP))
                .thenReturn(Optional.empty());
        when(preferenceRepository.findByUserIdAndTypeAndChannel(
                7L, NotificationType.HELP_ACCEPTED, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(preference(NotificationType.HELP_ACCEPTED,
                        NotificationChannel.EMAIL, true)));
        when(preferenceRepository.findByUserIdAndTypeAndChannel(
                7L, NotificationType.HELP_ACCEPTED, NotificationChannel.TELEGRAM))
                .thenReturn(Optional.empty());
        OutboxEvent enqueued = OutboxEvent.builder()
                .id(99L)
                .aggregateType("NOTIFICATION")
                .aggregateId(21L)
                .eventType("NOTIFICATION_DELIVERY")
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .availableAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        when(senderRegistry.isAvailable(NotificationChannel.EMAIL)).thenReturn(true);
        when(outboxService.enqueue(any(OutboxEvent.class))).thenReturn(enqueued);

        TransactionSynchronizationManager.initSynchronization();
        service.notify(command);

        verify(outboxDispatcher, never()).dispatchImmediately(anyLong());
        fireAfterCommit();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxService).enqueue(captor.capture());
        assertEquals("notification-delivery:21:EMAIL", captor.getValue().getDedupeKey());
        assertEquals(OutboxStatus.PENDING, captor.getValue().getStatus());
        verify(outboxDispatcher).dispatchImmediately(99L);
    }

    @Test
    void invalidCommandIsRejectedBeforeAnyWrite() {
        assertThrows(IllegalArgumentException.class, () -> service.notify(null));
        assertThrows(IllegalArgumentException.class, () -> service.notify(
                NotificationCommand.of(null, NotificationType.NEW_REVIEW, "t", "b", Map.of(), "k")));
        assertThrows(IllegalArgumentException.class, () -> service.notify(
                NotificationCommand.of(1L, null, "t", "b", Map.of(), "k")));

        verifyNoInteractions(notificationRepository);
    }

    @Test
    void validationErrorsRejectBlankTitleOrBody() {
        NotificationCommand command = NotificationCommand.of(
                7L, NotificationType.NEW_REVIEW, "   ", "body", Map.of(), null);

        assertThrows(IllegalArgumentException.class, () -> service.notify(command));
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void preferencesReturnTheFullMatrixWithServerDefaults() {
        when(preferenceRepository.findByUserId(7L)).thenReturn(List.of(
                preference(NotificationType.NEW_REVIEW, NotificationChannel.EMAIL, true),
                preference(NotificationType.NEW_REVIEW, NotificationChannel.IN_APP, false)));

        NotificationPreferencesResponse response = service.getPreferences(7L);

        int expected = NotificationType.values().length * NotificationChannel.values().length;
        assertEquals(expected, response.getPreferences().size());
        assertEquals(List.of(NotificationType.values()), response.getTypes());
        assertEquals(List.of(NotificationChannel.values()), response.getChannels());

        assertEquals(true, enabledOf(response, NotificationType.NEW_REVIEW, NotificationChannel.EMAIL));
        assertEquals(false, enabledOf(response, NotificationType.NEW_REVIEW, NotificationChannel.IN_APP));
        // Absent rows fall back to the documented defaults.
        assertEquals(true, enabledOf(response, NotificationType.HELP_ACCEPTED, NotificationChannel.IN_APP));
        assertEquals(false, enabledOf(response, NotificationType.HELP_ACCEPTED, NotificationChannel.EMAIL));
        assertEquals(false, enabledOf(response, NotificationType.HELP_ACCEPTED, NotificationChannel.TELEGRAM));
    }

    @Test
    void updatePreferencesUpsertsEveryRequestedPair() {
        UpdateNotificationPreferencesRequest request = new UpdateNotificationPreferencesRequest();
        UpdateNotificationPreferencesRequest.PreferenceUpdate first =
                new UpdateNotificationPreferencesRequest.PreferenceUpdate();
        first.setType(NotificationType.NEW_REVIEW);
        first.setChannel(NotificationChannel.EMAIL);
        first.setEnabled(true);
        UpdateNotificationPreferencesRequest.PreferenceUpdate second =
                new UpdateNotificationPreferencesRequest.PreferenceUpdate();
        second.setType(NotificationType.NEW_REVIEW);
        second.setChannel(NotificationChannel.IN_APP);
        second.setEnabled(false);
        request.setPreferences(List.of(first, second));

        when(preferenceRepository.findByUserId(7L)).thenReturn(List.of());

        service.updatePreferences(7L, request);

        verify(preferenceRepository).upsert(
                eq(7L), eq("NEW_REVIEW"), eq("EMAIL"), eq(true), any(LocalDateTime.class));
        verify(preferenceRepository).upsert(
                eq(7L), eq("NEW_REVIEW"), eq("IN_APP"), eq(false), any(LocalDateTime.class));
    }

    @Test
    void updatePreferencesRejectsEmptyOrIncompletePayload() {
        UpdateNotificationPreferencesRequest empty = new UpdateNotificationPreferencesRequest();
        empty.setPreferences(List.of());
        assertThrows(org.example.helptreeservice.exception.BadRequestException.class,
                () -> service.updatePreferences(7L, empty));

        UpdateNotificationPreferencesRequest incomplete = new UpdateNotificationPreferencesRequest();
        UpdateNotificationPreferencesRequest.PreferenceUpdate update =
                new UpdateNotificationPreferencesRequest.PreferenceUpdate();
        update.setType(NotificationType.NEW_REVIEW);
        incomplete.setPreferences(List.of(update));
        assertThrows(org.example.helptreeservice.exception.BadRequestException.class,
                () -> service.updatePreferences(7L, incomplete));

        verifyNoInteractions(preferenceRepository);
    }

    @Test
    void notificationsAreScopedToTheirOwner() {
        when(notificationRepository.findById(21L))
                .thenReturn(Optional.of(notification(21L, 8L, NotificationType.NEW_REVIEW)));

        assertThrows(NotFoundException.class, () -> service.markRead(7L, 21L));
        verify(notificationRepository, never()).markRead(anyLong(), anyLong(), any());
    }

    @Test
    void markReadOnlyUpdatesAnUnreadRow() {
        Notification unread = notification(21L, 7L, NotificationType.NEW_REVIEW);
        when(notificationRepository.findById(21L)).thenReturn(Optional.of(unread));
        when(notificationRepository.countByUserIdAndReadAtIsNull(7L)).thenReturn(0L);

        UnreadCountResponse response = service.markRead(7L, 21L);

        assertEquals(0L, response.getUnreadCount());
        verify(notificationRepository).markRead(eq(21L), eq(7L), any(LocalDateTime.class));
    }

    @Test
    void markReadDoesNotTouchAnAlreadyReadRow() {
        Notification read = notification(21L, 7L, NotificationType.NEW_REVIEW);
        read.setReadAt(LocalDateTime.now());
        when(notificationRepository.findById(21L)).thenReturn(Optional.of(read));
        when(notificationRepository.countByUserIdAndReadAtIsNull(7L)).thenReturn(0L);

        service.markRead(7L, 21L);

        verify(notificationRepository, never()).markRead(anyLong(), anyLong(), any());
    }

    @Test
    void markAllReadReturnsUpdatedCountAndRemainingUnread() {
        when(notificationRepository.markAllRead(eq(7L), any(LocalDateTime.class))).thenReturn(3);
        when(notificationRepository.countByUserIdAndReadAtIsNull(7L)).thenReturn(0L);

        MarkAllReadResponse response = service.markAllRead(7L);

        assertEquals(3, response.getUpdated());
        assertEquals(0L, response.getUnreadCount());
    }

    @Test
    void getNotificationsMapsThePageAndHonoursUnreadOnly() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Notification> page = new PageImpl<>(
                List.of(notification(21L, 7L, NotificationType.NEW_REVIEW)), pageable, 1);
        when(notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(7L, pageable))
                .thenReturn(page);

        Page<NotificationDto> result = service.getNotifications(7L, pageable, true);

        assertEquals(1, result.getTotalElements());
        assertEquals(21L, result.getContent().get(0).getId());
        verify(notificationRepository, never())
                .findByUserIdOrderByCreatedAtDescIdDesc(anyLong(), any());
    }

    @Test
    void globalKillSwitchSkipsCreation() {
        properties.setEnabled(false);

        NotificationDto dto = service.notify(NotificationCommand.of(
                7L, NotificationType.NEW_REVIEW, "t", "b", Map.of(), "k"));

        assertNull(dto);
        verifyNoInteractions(notificationRepository, outboxService, outboxDispatcher);
    }

    private void fireAfterCommit() {
        for (TransactionSynchronization synchronization
                : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }
    }

    private boolean enabledOf(NotificationPreferencesResponse response,
                              NotificationType type,
                              NotificationChannel channel) {
        return response.getPreferences().stream()
                .filter(item -> item.getType() == type && item.getChannel() == channel)
                .map(NotificationPreferenceDto::isEnabled)
                .findFirst()
                .orElseThrow();
    }

    private Notification notification(Long id, Long userId, NotificationType type) {
        Map<String, Object> payload = new LinkedHashMap<>();
        return Notification.builder()
                .id(id)
                .userId(userId)
                .type(type)
                .title("title")
                .body("body")
                .payload(payload)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private org.example.helptreeservice.entity.NotificationPreference preference(
            NotificationType type, NotificationChannel channel, boolean enabled) {
        return org.example.helptreeservice.entity.NotificationPreference.builder()
                .id(1L)
                .userId(7L)
                .type(type)
                .channel(channel)
                .enabled(enabled)
                .build();
    }
}
