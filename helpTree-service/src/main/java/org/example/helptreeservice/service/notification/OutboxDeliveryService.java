package org.example.helptreeservice.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.NotificationProperties;
import org.example.helptreeservice.entity.Notification;
import org.example.helptreeservice.entity.OutboxEvent;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.NotificationChannel;
import org.example.helptreeservice.enums.OutboxStatus;
import org.example.helptreeservice.repository.NotificationPreferenceRepository;
import org.example.helptreeservice.repository.NotificationRepository;
import org.example.helptreeservice.repository.OutboxEventRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Performs one delivery attempt for a claimed outbox event.
 *
 * <p>The attempt runs in its own transaction so a network call never holds the
 * claim lock. On a transient failure the event becomes {@code FAILED} with an
 * exponential backoff; after {@code maxAttempts} it becomes {@code DEAD}. A
 * permanent failure (missing recipient, unknown channel) skips retries.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxDeliveryService {

    private static final int MAX_ERROR_LENGTH = 2000;

    private final OutboxEventRepository outboxEventRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final NotificationSenderRegistry senderRegistry;
    private final NotificationProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(Long eventId) {
        OutboxEvent event = outboxEventRepository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != OutboxStatus.PROCESSING) {
            return;
        }

        try {
            DeliveryOutcome outcome = attempt(event);
            if (outcome == DeliveryOutcome.SENT) {
                markPublished(event, null);
            } else {
                markPublished(event, "Доставка не требуется");
            }
        } catch (PermanentDeliveryException e) {
            markDead(event, e.getMessage());
        } catch (RuntimeException e) {
            markFailed(event, e.getMessage());
        }
    }

    private DeliveryOutcome attempt(OutboxEvent event) {
        Map<String, Object> payload = event.getPayload() == null ? Map.of() : event.getPayload();
        Long notificationId = asLong(payload.get("notificationId"));
        Long userId = asLong(payload.get("userId"));
        NotificationChannel channel = parseChannel(payload.get("channel"));

        if (notificationId == null || userId == null || channel == null) {
            throw new PermanentDeliveryException("Некорректный payload outbox-события");
        }
        if (channel == NotificationChannel.IN_APP) {
            throw new PermanentDeliveryException(
                    "IN_APP доставляется WebSocket-каналом, а не outbox-воркером");
        }

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new PermanentDeliveryException(
                        "Уведомление " + notificationId + " больше не существует"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new PermanentDeliveryException(
                        "Получатель " + userId + " больше не существует"));
        if (Boolean.TRUE.equals(user.getDeleted()) || user.getAnonymizedAt() != null) {
            throw new PermanentDeliveryException("Аккаунт получателя удалён");
        }

        boolean stillEnabled = preferenceRepository
                .findByUserIdAndTypeAndChannel(userId, notification.getType(), channel)
                .map(preference -> Boolean.TRUE.equals(preference.getEnabled()))
                .orElse(false);
        if (!stillEnabled) {
            log.debug("Канал {} отключён пользователем {} после создания уведомления {}",
                    channel, userId, notificationId);
            return DeliveryOutcome.SKIPPED;
        }

        NotificationSender sender = senderRegistry.find(channel)
                .orElseThrow(() -> new PermanentDeliveryException(
                        "Транспорт " + channel + " не зарегистрирован"));
        if (!sender.isAvailable()) {
            // Configuration can be restored, so this is retried with backoff.
            throw new IllegalStateException("Транспорт " + channel + " не настроен");
        }

        String recipient = channel == NotificationChannel.EMAIL
                ? user.getEmail()
                : user.getTelegramChatId();
        if (recipient == null || recipient.isBlank()) {
            throw new PermanentDeliveryException(
                    "У пользователя " + userId + " нет получателя для канала " + channel);
        }

        NotificationDelivery delivery = new NotificationDelivery(
                notification.getId(),
                userId,
                channel,
                recipient,
                notification.getTitle(),
                notification.getBody(),
                notification.getPayload());
        if (!sender.send(delivery)) {
            // The transport reports transient problems by returning false; the
            // outbox retries with backoff instead of silently dropping the mail.
            throw new IllegalStateException("Транспорт " + channel + " не доставил уведомление");
        }
        return DeliveryOutcome.SENT;
    }

    private void markPublished(OutboxEvent event, String note) {
        LocalDateTime now = LocalDateTime.now();
        event.setStatus(OutboxStatus.PUBLISHED);
        event.setPublishedAt(now);
        event.setLockedAt(null);
        event.setLockedBy(null);
        event.setLastError(note);
        outboxEventRepository.save(event);
        log.debug("Outbox-событие {} опубликовано", event.getId());
    }

    private void markDead(OutboxEvent event, String error) {
        event.setStatus(OutboxStatus.DEAD);
        event.setLockedAt(null);
        event.setLockedBy(null);
        event.setLastError(truncate(error));
        outboxEventRepository.save(event);
        log.warn("Outbox-событие {} переведено в DEAD: {}", event.getId(), error);
    }

    private void markFailed(OutboxEvent event, String error) {
        int attempts = event.getAttempts() == null ? 1 : event.getAttempts();
        int maxAttempts = Math.max(1, properties.getMaxAttempts());
        LocalDateTime now = LocalDateTime.now();

        if (attempts >= maxAttempts) {
            markDead(event, "Исчерпаны попытки доставки: " + error);
            return;
        }

        event.setStatus(OutboxStatus.FAILED);
        event.setAvailableAt(now.plusSeconds(backoffSeconds(attempts)));
        event.setLockedAt(null);
        event.setLockedBy(null);
        event.setLastError(truncate(error));
        outboxEventRepository.save(event);
        log.warn("Outbox-событие {} не доставлено (попытка {}/{}), повтор позже: {}",
                event.getId(), attempts, maxAttempts, error);
    }

    long backoffSeconds(int attempts) {
        long base = Math.max(1, properties.getRetryBaseSeconds());
        long max = Math.max(base, properties.getRetryMaxSeconds());
        int exponent = Math.min(Math.max(attempts - 1, 0), 20);
        long delay = base * (1L << exponent);
        return Math.min(delay, max);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private NotificationChannel parseChannel(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return NotificationChannel.valueOf(value.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= MAX_ERROR_LENGTH
                ? value
                : value.substring(0, MAX_ERROR_LENGTH);
    }

    private enum DeliveryOutcome {
        SENT,
        SKIPPED
    }
}
