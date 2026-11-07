package org.example.helptreeservice.block17;

import org.example.helptreeservice.dto.notifications.NotificationDto;
import org.example.helptreeservice.dto.notifications.NotificationPreferencesResponse;
import org.example.helptreeservice.dto.notifications.UpdateNotificationPreferencesRequest;
import org.example.helptreeservice.entity.Notification;
import org.example.helptreeservice.entity.OutboxEvent;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.NotificationChannel;
import org.example.helptreeservice.enums.NotificationType;
import org.example.helptreeservice.enums.OutboxStatus;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.NotificationRepository;
import org.example.helptreeservice.repository.OutboxEventRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.notification.NotificationCommand;
import org.example.helptreeservice.service.notification.NotificationService;
import org.example.helptreeservice.service.notification.OutboxDeliveryService;
import org.example.helptreeservice.service.notification.OutboxService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-database proof for Block 17.
 *
 * <p>Runs with {@code INTEGRATION_TEST=true}. Each created user has a unique
 * email prefix and cleanup only touches this test's rows.</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class NotificationIntegrationTest {

    private static final String EMAIL_PREFIX = "block17-it-";
    private static final String DEDUPE_PREFIX = "block17-it-";

    @Autowired private NotificationService notificationService;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private OutboxService outboxService;
    @Autowired private OutboxDeliveryService outboxDeliveryService;
    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private SimpMessagingTemplate messagingTemplate;

    @AfterEach
    void cleanUp() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE email LIKE ?", Long.class, EMAIL_PREFIX + "%@test.local");
        if (ids.isEmpty()) {
            return;
        }
        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        jdbcTemplate.update("DELETE FROM outbox_events WHERE dedupe_key LIKE ?",
                DEDUPE_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM notifications WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM notification_preferences WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (" + in + ")");
    }

    @Test
    void aRolledBackTransactionLeavesNeitherNotificationNorOutboxIntent() {
        User user = createUser("rollback");
        String dedupeKey = DEDUPE_PREFIX + "rollback:" + UUID.randomUUID();
        String outboxKey = DEDUPE_PREFIX + "rollback-outbox:" + UUID.randomUUID();
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        template.executeWithoutResult(status -> {
            notificationService.notify(NotificationCommand.of(
                    user.getId(),
                    NotificationType.NEW_REVIEW,
                    "Новый отзыв",
                    "Вам оставили отзыв.",
                    Map.of("reviewId", 1L),
                    dedupeKey));
            outboxService.enqueue(OutboxEvent.builder()
                    .aggregateType("NOTIFICATION")
                    .aggregateId(1L)
                    .eventType("NOTIFICATION_DELIVERY")
                    .payload(Map.of("channel", NotificationChannel.EMAIL.name()))
                    .dedupeKey(outboxKey)
                    .status(OutboxStatus.PENDING)
                    .attempts(0)
                    .availableAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
                    .build());
            // The business operation fails after the notification was scheduled.
            status.setRollbackOnly();
        });

        assertEquals(0L, countNotifications(dedupeKey));
        assertEquals(0L, countOutboxByKey(outboxKey));
        assertTrue(notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(
                user.getId(), PageRequest.of(0, 10)).isEmpty());
    }

    @Test
    void theSameDedupeKeyProducesExactlyOneRow() {
        User user = createUser("dedupe");
        String dedupeKey = DEDUPE_PREFIX + "dedupe:" + UUID.randomUUID();
        NotificationCommand command = NotificationCommand.of(
                user.getId(),
                NotificationType.NEW_REVIEW,
                "Новый отзыв",
                "Вам оставили отзыв.",
                Map.of("reviewId", 2L),
                dedupeKey);

        NotificationDto first = notificationService.notify(command);
        NotificationDto second = notificationService.notify(command);

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.getId(), second.getId());
        assertEquals(1L, countNotifications(dedupeKey));
    }

    @Test
    void readAndReadAllUpdateOnlyTheOwnersRows() {
        User owner = createUser("owner");
        User stranger = createUser("stranger");
        NotificationDto first = notificationService.notify(notification(owner, "read:1"));
        NotificationDto second = notificationService.notify(notification(owner, "read:2"));
        NotificationDto third = notificationService.notify(notification(owner, "read:3"));
        NotificationDto foreign = notificationService.notify(notification(stranger, "read:foreign"));

        assertEquals(3L, notificationService.getUnreadCount(owner.getId()).getUnreadCount());

        assertEquals(2L,
                notificationService.markRead(owner.getId(), first.getId()).getUnreadCount());
        assertThrows(NotFoundException.class,
                () -> notificationService.markRead(owner.getId(), foreign.getId()));

        assertEquals(2,
                notificationService.markAllRead(owner.getId()).getUpdated());
        assertEquals(0L,
                notificationService.getUnreadCount(owner.getId()).getUnreadCount());
        assertEquals(3,
                notificationService.getNotifications(owner.getId(), PageRequest.of(0, 10), false)
                        .getTotalElements());
        assertEquals(0,
                notificationService.getNotifications(owner.getId(), PageRequest.of(0, 10), true)
                        .getTotalElements());

        // The other user's row stays unread and untouched.
        assertFalse(notificationService.getNotifications(
                stranger.getId(), PageRequest.of(0, 10), false).getContent().get(0).isRead());
        assertTrue(second.getId() > 0L && third.getId() > 0L);
    }

    @Test
    void preferencesAreUpsertedAndTheFullMatrixUsesServerDefaults() {
        User user = createUser("prefs");

        NotificationPreferencesResponse defaults = notificationService.getPreferences(user.getId());
        assertEquals(true, enabledOf(defaults, NotificationType.NEW_REVIEW, NotificationChannel.IN_APP));
        assertEquals(false, enabledOf(defaults, NotificationType.NEW_REVIEW, NotificationChannel.EMAIL));
        assertEquals(false, enabledOf(defaults, NotificationType.NEW_REVIEW, NotificationChannel.TELEGRAM));

        notificationService.updatePreferences(user.getId(), preferences(
                preference(NotificationType.NEW_REVIEW, NotificationChannel.IN_APP, false),
                preference(NotificationType.NEW_REVIEW, NotificationChannel.EMAIL, true)));

        NotificationPreferencesResponse updated = notificationService.getPreferences(user.getId());
        assertEquals(false, enabledOf(updated, NotificationType.NEW_REVIEW, NotificationChannel.IN_APP));
        assertEquals(true, enabledOf(updated, NotificationType.NEW_REVIEW, NotificationChannel.EMAIL));

        // A second update of the same pair must update the row, not add a duplicate.
        notificationService.updatePreferences(user.getId(), preferences(
                preference(NotificationType.NEW_REVIEW, NotificationChannel.EMAIL, false)));

        NotificationPreferencesResponse second = notificationService.getPreferences(user.getId());
        assertEquals(false, enabledOf(second, NotificationType.NEW_REVIEW, NotificationChannel.EMAIL));
        assertEquals(2L, countPreferences(user.getId()));
    }

    @Test
    void outboxClaimsAreExclusiveAndStaleClaimsReturnToTheQueue() {
        User user = createUser("outbox");
        NotificationDto notification = notificationService.notify(notification(user, "outbox:1"));

        OutboxEvent first = enqueueEvent(notification.getId(), user.getId(),
                NotificationChannel.EMAIL, "outbox:" + UUID.randomUUID());
        OutboxEvent second = enqueueEvent(notification.getId(), user.getId(),
                NotificationChannel.EMAIL, "outbox:" + UUID.randomUUID());

        List<OutboxService.OutboxClaim> claims = outboxService.claimBatch("block17-it", 50);
        List<Long> claimedIds = claims.stream()
                .map(OutboxService.OutboxClaim::eventId)
                .toList();
        assertTrue(claimedIds.containsAll(List.of(first.getId(), second.getId())));
        assertEquals(OutboxStatus.PROCESSING, outboxEventRepository.findById(first.getId())
                .orElseThrow().getStatus());
        assertEquals(1, outboxEventRepository.findById(first.getId()).orElseThrow().getAttempts());

        // A second worker must not be able to claim an already-claimed row.
        assertNull(outboxService.claimById("block17-it-2", first.getId()));

        jdbcTemplate.update("UPDATE outbox_events SET locked_at = ? WHERE id IN (?, ?)",
                LocalDateTime.now().minusMinutes(30), first.getId(), second.getId());

        outboxService.releaseStaleClaims(LocalDateTime.now().minusMinutes(10));

        assertEquals(OutboxStatus.FAILED,
                outboxEventRepository.findById(first.getId()).orElseThrow().getStatus());
        // The released event is dispatchable again.
        assertNotNull(outboxService.claimById("block17-it-3", first.getId()));
    }

    @Test
    void aDisabledChannelIsPublishedWithoutCallingATransport() {
        User user = createUser("skip");
        NotificationDto notification = notificationService.notify(notification(user, "skip:1"));

        // No EMAIL preference row exists, so EMAIL defaults to disabled.
        OutboxEvent event = enqueueEvent(notification.getId(), user.getId(),
                NotificationChannel.EMAIL, "skip:" + UUID.randomUUID());
        OutboxService.OutboxClaim claim = outboxService.claimById("block17-it", event.getId());
        assertNotNull(claim);

        outboxDeliveryService.deliver(event.getId());

        OutboxEvent stored = outboxEventRepository.findById(event.getId()).orElseThrow();
        assertEquals(OutboxStatus.PUBLISHED, stored.getStatus());
        assertEquals("Доставка не требуется", stored.getLastError());
    }

    @Test
    void anInvalidEventBecomesDeadInsteadOfRetryingForever() {
        User user = createUser("dead");
        NotificationDto notification = notificationService.notify(notification(user, "dead:1"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", user.getId());
        payload.put("channel", NotificationChannel.EMAIL.name());
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("NOTIFICATION")
                .aggregateId(notification.getId())
                .eventType("NOTIFICATION_DELIVERY")
                .payload(payload)
                .dedupeKey(DEDUPE_PREFIX + "dead:" + UUID.randomUUID())
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .availableAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        event = outboxEventRepository.saveAndFlush(event);

        outboxService.claimById("block17-it", event.getId());
        outboxDeliveryService.deliver(event.getId());

        assertEquals(OutboxStatus.DEAD,
                outboxEventRepository.findById(event.getId()).orElseThrow().getStatus());
    }

    private NotificationCommand notification(User user, String suffix) {
        return NotificationCommand.of(
                user.getId(),
                NotificationType.NEW_REVIEW,
                "Новый отзыв",
                "Вам оставили отзыв.",
                Map.of("suffix", suffix),
                DEDUPE_PREFIX + suffix + ":" + UUID.randomUUID());
    }

    private OutboxEvent enqueueEvent(Long notificationId,
                                     Long userId,
                                     NotificationChannel channel,
                                     String suffix) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("notificationId", notificationId);
        payload.put("userId", userId);
        payload.put("channel", channel.name());
        payload.put("title", "title");
        payload.put("body", "body");
        return outboxService.enqueue(OutboxEvent.builder()
                .aggregateType("NOTIFICATION")
                .aggregateId(notificationId)
                .eventType("NOTIFICATION_DELIVERY")
                .payload(payload)
                .dedupeKey(DEDUPE_PREFIX + "delivery:" + suffix)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .availableAt(LocalDateTime.now().minusSeconds(5))
                .createdAt(LocalDateTime.now())
                .build());
    }

    private long countNotifications(String dedupeKey) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE dedupe_key = ?", Long.class, dedupeKey);
        return count == null ? 0L : count;
    }

    private long countOutboxByKey(String dedupeKey) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE dedupe_key = ?",
                Long.class, dedupeKey);
        return count == null ? 0L : count;
    }

    private long countPreferences(Long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notification_preferences WHERE user_id = ?",
                Long.class, userId);
        return count == null ? 0L : count;
    }

    private User createUser(String name) {
        User user = new User();
        user.setName(name);
        user.setEmail(EMAIL_PREFIX + UUID.randomUUID() + "@test.local");
        user.setPassword("$2a$10$test-only-not-a-real-hash");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.saveAndFlush(user);
    }

    private boolean enabledOf(NotificationPreferencesResponse response,
                              NotificationType type,
                              NotificationChannel channel) {
        return response.getPreferences().stream()
                .filter(item -> item.getType() == type && item.getChannel() == channel)
                .findFirst()
                .orElseThrow()
                .isEnabled();
    }

    private UpdateNotificationPreferencesRequest preferences(
            UpdateNotificationPreferencesRequest.PreferenceUpdate... updates) {
        UpdateNotificationPreferencesRequest request = new UpdateNotificationPreferencesRequest();
        request.setPreferences(List.of(updates));
        return request;
    }

    private UpdateNotificationPreferencesRequest.PreferenceUpdate preference(
            NotificationType type, NotificationChannel channel, boolean enabled) {
        UpdateNotificationPreferencesRequest.PreferenceUpdate update =
                new UpdateNotificationPreferencesRequest.PreferenceUpdate();
        update.setType(type);
        update.setChannel(channel);
        update.setEnabled(enabled);
        return update;
    }
}
