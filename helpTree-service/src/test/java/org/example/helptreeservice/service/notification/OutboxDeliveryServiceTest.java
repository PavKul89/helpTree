package org.example.helptreeservice.service.notification;

import org.example.helptreeservice.config.NotificationProperties;
import org.example.helptreeservice.entity.Notification;
import org.example.helptreeservice.entity.NotificationPreference;
import org.example.helptreeservice.entity.OutboxEvent;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.NotificationChannel;
import org.example.helptreeservice.enums.NotificationType;
import org.example.helptreeservice.enums.OutboxStatus;
import org.example.helptreeservice.metrics.HelpTreeMetrics;
import org.example.helptreeservice.repository.NotificationPreferenceRepository;
import org.example.helptreeservice.repository.NotificationRepository;
import org.example.helptreeservice.repository.OutboxEventRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxDeliveryServiceTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationPreferenceRepository preferenceRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationSenderRegistry senderRegistry;
    @Mock private NotificationSender sender;
    @Mock private HelpTreeMetrics metrics;

    private NotificationProperties properties;
    private OutboxDeliveryService service;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.setMaxAttempts(3);
        properties.setRetryBaseSeconds(30);
        properties.setRetryMaxSeconds(120);
        service = new OutboxDeliveryService(
                outboxEventRepository,
                notificationRepository,
                preferenceRepository,
                userRepository,
                senderRegistry,
                properties,
                metrics);
    }

    @Test
    void successfulDeliveryMarksTheEventPublished() {
        OutboxEvent event = event(100L, 2, NotificationChannel.EMAIL);
        stubDelivery(event, true, true);

        service.deliver(100L);

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertNotNull(event.getPublishedAt());
        assertNull(event.getLockedAt());
        assertNull(event.getLockedBy());
        assertNull(event.getLastError());
        verify(sender).send(any(NotificationDelivery.class));
    }

    @Test
    void disabledChannelAfterEnqueueIsSkippedWithoutSending() {
        OutboxEvent event = event(100L, 1, NotificationChannel.EMAIL);
        when(outboxEventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(notificationRepository.findById(50L)).thenReturn(Optional.of(notification()));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user()));
        when(preferenceRepository.findByUserIdAndTypeAndChannel(
                7L, NotificationType.NEW_REVIEW, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(preference(false)));

        service.deliver(100L);

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        assertEquals("Доставка не требуется", event.getLastError());
        verify(senderRegistry, never()).find(any());
        verify(sender, never()).send(any());
    }

    @Test
    void transientTransportFailureBacksOff() {
        OutboxEvent event = event(100L, 2, NotificationChannel.EMAIL);
        stubDelivery(event, false, true);

        service.deliver(100L);

        assertEquals(OutboxStatus.FAILED, event.getStatus());
        assertNull(event.getLockedAt());
        assertNotNull(event.getLastError());
        assertTrue(event.getAvailableAt().isAfter(LocalDateTime.now()));
        // attempts already counted at claim time: 2 -> one doubling of the base.
        assertEquals(60, service.backoffSeconds(2));
        assertTrue(event.getAvailableAt().isBefore(LocalDateTime.now().plusSeconds(90)));
    }

    @Test
    void exhaustedAttemptsMoveTheEventToDead() {
        OutboxEvent event = event(100L, 3, NotificationChannel.EMAIL);
        stubDelivery(event, false, true);

        service.deliver(100L);

        assertEquals(OutboxStatus.DEAD, event.getStatus());
        assertTrue(event.getLastError().contains("Исчерпаны попытки"));
        assertNull(event.getLockedAt());
    }

    @Test
    void permanentFailureMovesTheEventToDeadWithoutRetry() {
        OutboxEvent event = event(100L, 1, NotificationChannel.EMAIL);
        Map<String, Object> payload = new LinkedHashMap<>(event.getPayload());
        payload.remove("notificationId");
        event.setPayload(payload);
        when(outboxEventRepository.findById(100L)).thenReturn(Optional.of(event));

        service.deliver(100L);

        assertEquals(OutboxStatus.DEAD, event.getStatus());
        assertTrue(event.getLastError().contains("payload"));
        verify(sender, never()).send(any());
    }

    @Test
    void missingNotificationIsPermanent() {
        OutboxEvent event = event(100L, 1, NotificationChannel.EMAIL);
        when(outboxEventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(notificationRepository.findById(50L)).thenReturn(Optional.empty());

        service.deliver(100L);

        assertEquals(OutboxStatus.DEAD, event.getStatus());
        assertTrue(event.getLastError().contains("больше не существует"));
    }

    @Test
    void deletedRecipientIsPermanent() {
        OutboxEvent event = event(100L, 1, NotificationChannel.EMAIL);
        when(outboxEventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(notificationRepository.findById(50L)).thenReturn(Optional.of(notification()));
        User deleted = user();
        deleted.setDeleted(true);
        when(userRepository.findById(7L)).thenReturn(Optional.of(deleted));

        service.deliver(100L);

        assertEquals(OutboxStatus.DEAD, event.getStatus());
    }

    @Test
    void missingRecipientAddressIsPermanent() {
        OutboxEvent event = event(100L, 1, NotificationChannel.TELEGRAM);
        when(outboxEventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(notificationRepository.findById(50L)).thenReturn(Optional.of(notification()));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user()));
        when(preferenceRepository.findByUserIdAndTypeAndChannel(
                7L, NotificationType.NEW_REVIEW, NotificationChannel.TELEGRAM))
                .thenReturn(Optional.of(preference(true)));
        when(senderRegistry.find(NotificationChannel.TELEGRAM)).thenReturn(Optional.of(sender));
        when(sender.isAvailable()).thenReturn(true);

        service.deliver(100L);

        assertEquals(OutboxStatus.DEAD, event.getStatus());
        assertTrue(event.getLastError().contains("нет получателя"));
        verify(sender, never()).send(any());
    }

    @Test
    void missingTransportConfigurationIsTransient() {
        OutboxEvent event = event(100L, 1, NotificationChannel.EMAIL);
        when(outboxEventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(notificationRepository.findById(50L)).thenReturn(Optional.of(notification()));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user()));
        when(preferenceRepository.findByUserIdAndTypeAndChannel(
                7L, NotificationType.NEW_REVIEW, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(preference(true)));
        when(senderRegistry.find(NotificationChannel.EMAIL)).thenReturn(Optional.of(sender));
        when(sender.isAvailable()).thenReturn(false);

        service.deliver(100L);

        assertEquals(OutboxStatus.FAILED, event.getStatus());
        verify(sender, never()).send(any());
    }

    @Test
    void onlyProcessingEventsAreDelivered() {
        OutboxEvent event = event(100L, 1, NotificationChannel.EMAIL);
        event.setStatus(OutboxStatus.PUBLISHED);
        when(outboxEventRepository.findById(100L)).thenReturn(Optional.of(event));

        service.deliver(100L);

        assertEquals(OutboxStatus.PUBLISHED, event.getStatus());
        verify(outboxEventRepository, never()).save(event);
    }

    @Test
    void backoffDoublesAndRespectsTheCeiling() {
        assertEquals(30, service.backoffSeconds(1));
        assertEquals(60, service.backoffSeconds(2));
        assertEquals(120, service.backoffSeconds(3));
        assertEquals(120, service.backoffSeconds(30));
    }

    private void stubDelivery(OutboxEvent event, boolean sendResult, boolean available) {
        when(outboxEventRepository.findById(100L)).thenReturn(Optional.of(event));
        when(notificationRepository.findById(50L)).thenReturn(Optional.of(notification()));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user()));
        when(preferenceRepository.findByUserIdAndTypeAndChannel(
                7L, NotificationType.NEW_REVIEW, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(preference(true)));
        when(senderRegistry.find(NotificationChannel.EMAIL)).thenReturn(Optional.of(sender));
        when(sender.isAvailable()).thenReturn(available);
        when(sender.send(any(NotificationDelivery.class))).thenReturn(sendResult);
    }

    private OutboxEvent event(Long id, int attempts, NotificationChannel channel) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", 50L);
        payload.put("userId", 7L);
        payload.put("channel", channel.name());
        payload.put("title", "title");
        payload.put("body", "body");
        return OutboxEvent.builder()
                .id(id)
                .aggregateType("NOTIFICATION")
                .aggregateId(50L)
                .eventType("NOTIFICATION_DELIVERY")
                .payload(payload)
                .dedupeKey("notification-delivery:50:" + channel.name())
                .status(OutboxStatus.PROCESSING)
                .attempts(attempts)
                .availableAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Notification notification() {
        return Notification.builder()
                .id(50L)
                .userId(7L)
                .type(NotificationType.NEW_REVIEW)
                .title("title")
                .body("body")
                .payload(new LinkedHashMap<>())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private User user() {
        User user = new User();
        user.setId(7L);
        user.setName("recipient");
        user.setEmail("recipient@test.local");
        return user;
    }

    private NotificationPreference preference(boolean enabled) {
        return NotificationPreference.builder()
                .id(1L)
                .userId(7L)
                .type(NotificationType.NEW_REVIEW)
                .channel(NotificationChannel.EMAIL)
                .enabled(enabled)
                .build();
    }
}
