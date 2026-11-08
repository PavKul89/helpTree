package org.example.helptreeservice.service.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.NotificationProperties;
import org.example.helptreeservice.config.FeatureFlags;
import org.example.helptreeservice.enums.FeatureFlag;
import org.example.helptreeservice.dto.notifications.MarkAllReadResponse;
import org.example.helptreeservice.dto.notifications.NotificationDto;
import org.example.helptreeservice.dto.notifications.NotificationPreferenceDto;
import org.example.helptreeservice.dto.notifications.NotificationPreferencesResponse;
import org.example.helptreeservice.dto.notifications.UnreadCountResponse;
import org.example.helptreeservice.dto.notifications.UpdateNotificationPreferencesRequest;
import org.example.helptreeservice.entity.Notification;
import org.example.helptreeservice.entity.NotificationPreference;
import org.example.helptreeservice.entity.OutboxEvent;
import org.example.helptreeservice.enums.NotificationChannel;
import org.example.helptreeservice.enums.NotificationType;
import org.example.helptreeservice.enums.OutboxStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.NotificationPreferenceRepository;
import org.example.helptreeservice.repository.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Single entry point for creating and reading notifications.
 *
 * <p>Guarantees:</p>
 * <ul>
 *   <li>the canonical row is written in the caller's transaction, so a rollback
 *       leaves no notification and no outbox intent behind;</li>
 *   <li>creation is idempotent through a user-scoped {@code dedupe_key}; a replay
 *       returns the existing row and never schedules a second delivery;</li>
 *   <li>WebSocket pushes and outbox dispatch start only after a successful
 *       commit;</li>
 *   <li>preferences control delivery only. The in-app history row always exists
 *       so a user never "loses" an event by disabling a channel.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int TITLE_MAX_LENGTH = 200;
    private static final int BODY_MAX_LENGTH = 4000;

    /**
     * Spring Boot 4 configures Jackson 3 ({@code tools.jackson}), so there is no
     * {@code com.fasterxml.jackson.databind.ObjectMapper} bean. The service only
     * needs to serialize a small map for the native insert, exactly like the
     * request filters do, so it owns a private mapper. Java time must be
     * registered explicitly: account-block notifications carry timestamps, and
     * ISO-8601 strings match what Hibernate writes for the JPA path.
     */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationRealtimeService realtimeService;
    private final NotificationSenderRegistry senderRegistry;
    private final OutboxService outboxService;
    private final OutboxDispatcher outboxDispatcher;
    private final NotificationProperties properties;
    private final FeatureFlags featureFlags;

    // ============================================================
    // Creation
    // ============================================================

    /**
     * Creates the canonical notification and the delivery intents. Must be
     * called from the business transaction that produced the event.
     *
     * @return the created (or previously existing) notification, or {@code null}
     *         when notifications are globally disabled
     */
    @Transactional
    public NotificationDto notify(NotificationCommand command) {
        validate(command);
        if (featureFlags.isDisabled(FeatureFlag.NOTIFICATIONS)) {
            log.debug("Уведомления отключены, событие {} для пользователя {} пропущено",
                    command.type(), command.userId());
            return null;
        }

        String title = truncate(trimToNull(command.title()), TITLE_MAX_LENGTH);
        String body = truncate(trimToNull(command.body()), BODY_MAX_LENGTH);
        if (title == null || body == null) {
            throw new IllegalArgumentException("Уведомление должно содержать заголовок и текст");
        }

        Map<String, Object> payload = command.payload() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(command.payload());
        LocalDateTime now = LocalDateTime.now();

        Notification notification = insertCanonical(command, title, body, payload, now);
        if (notification == null) {
            // A concurrent or repeated event already produced this notification.
            Notification existing = command.dedupeKey() == null
                    ? null
                    : notificationRepository
                            .findByUserIdAndDedupeKey(command.userId(), command.dedupeKey())
                            .orElse(null);
            if (existing == null) {
                log.debug("Уведомление {} для пользователя {} уже существует",
                        command.type(), command.userId());
                return null;
            }
            return toDto(existing);
        }

        NotificationDto dto = toDto(notification);
        boolean pushInApp = isChannelEnabled(
                command.userId(), command.type(), NotificationChannel.IN_APP);
        List<Long> outboxIds = enqueueExternalChannels(command, notification, payload, now);

        dispatchAfterCommit(dto, pushInApp, outboxIds);
        return dto;
    }

    private Notification insertCanonical(NotificationCommand command,
                                         String title,
                                         String body,
                                         Map<String, Object> payload,
                                         LocalDateTime now) {
        if (command.dedupeKey() == null) {
            return notificationRepository.save(Notification.builder()
                    .userId(command.userId())
                    .type(command.type())
                    .title(title)
                    .body(body)
                    .payload(payload)
                    .dedupeKey(null)
                    .createdAt(now)
                    .build());
        }

        int inserted = notificationRepository.insertIfAbsent(
                command.userId(),
                command.type().name(),
                title,
                body,
                serializePayload(payload),
                command.dedupeKey(),
                now);
        if (inserted == 0) {
            return null;
        }
        return notificationRepository
                .findByUserIdAndDedupeKey(command.userId(), command.dedupeKey())
                .orElseThrow(() -> new IllegalStateException(
                        "Уведомление не найдено после вставки: " + command.dedupeKey()));
    }

    private List<Long> enqueueExternalChannels(NotificationCommand command,
                                               Notification notification,
                                               Map<String, Object> payload,
                                               LocalDateTime now) {
        List<Long> eventIds = new ArrayList<>();
        for (NotificationChannel channel : List.of(
                NotificationChannel.EMAIL, NotificationChannel.TELEGRAM)) {
            if (!isChannelEnabled(command.userId(), command.type(), channel)) {
                continue;
            }
            if (!senderRegistry.isAvailable(channel)) {
                log.debug("Канал {} не настроен, доставка уведомления {} пропущена",
                        channel, notification.getId());
                continue;
            }

            Map<String, Object> eventPayload = new LinkedHashMap<>();
            eventPayload.put("notificationId", notification.getId());
            eventPayload.put("userId", command.userId());
            eventPayload.put("channel", channel.name());
            eventPayload.put("notificationType", command.type().name());
            // A shallow copy keeps the outbox event self-contained for debugging
            // and lets the worker render a message without loading the row twice.
            eventPayload.put("title", notification.getTitle());
            eventPayload.put("body", notification.getBody());

            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType("NOTIFICATION")
                    .aggregateId(notification.getId())
                    .eventType("NOTIFICATION_DELIVERY")
                    .payload(eventPayload)
                    .dedupeKey("notification-delivery:"
                            + notification.getId() + ":" + channel.name())
                    .status(OutboxStatus.PENDING)
                    .attempts(0)
                    .availableAt(now)
                    .createdAt(now)
                    .build();
            eventIds.add(outboxService.enqueue(event).getId());
        }
        return eventIds;
    }

    /**
     * The REST row is the source of truth. The push and the immediate outbox
     * attempt are best-effort add-ons that run only after the commit; a broker
     * or SMTP outage can never roll back the business transaction.
     */
    private void dispatchAfterCommit(NotificationDto dto,
                                     boolean pushInApp,
                                     List<Long> outboxIds) {
        Runnable action = () -> {
            if (pushInApp) {
                realtimeService.push(dto.getUserId(), dto);
            }
            for (Long eventId : outboxIds) {
                outboxDispatcher.dispatchImmediately(eventId);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    });
        } else {
            action.run();
        }
    }

    // ============================================================
    // Read / read-state
    // ============================================================

    @Transactional(readOnly = true)
    public Page<NotificationDto> getNotifications(Long userId,
                                                  Pageable pageable,
                                                  boolean unreadOnly) {
        featureFlags.requireEnabled(FeatureFlag.NOTIFICATIONS);
        Page<Notification> page = unreadOnly
                ? notificationRepository.findByUserIdAndReadAtIsNullOrderByCreatedAtDescIdDesc(
                        userId, pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
        return page.map(this::toDto);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long userId) {
        featureFlags.requireEnabled(FeatureFlag.NOTIFICATIONS);
        return new UnreadCountResponse(
                notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public UnreadCountResponse markRead(Long userId, Long notificationId) {
        featureFlags.requireEnabled(FeatureFlag.NOTIFICATIONS);
        Notification notification = notificationRepository.findById(notificationId)
                .filter(item -> item.getUserId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Уведомление не найдено"));

        if (notification.getReadAt() == null) {
            notificationRepository.markRead(notificationId, userId, LocalDateTime.now());
        }
        return new UnreadCountResponse(
                notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public MarkAllReadResponse markAllRead(Long userId) {
        featureFlags.requireEnabled(FeatureFlag.NOTIFICATIONS);
        int updated = notificationRepository.markAllRead(userId, LocalDateTime.now());
        return new MarkAllReadResponse(
                updated, notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    // ============================================================
    // Preferences
    // ============================================================

    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getPreferences(Long userId) {
        featureFlags.requireEnabled(FeatureFlag.NOTIFICATIONS);
        Map<String, Boolean> stored = new LinkedHashMap<>();
        for (NotificationPreference preference : preferenceRepository.findByUserId(userId)) {
            stored.put(key(preference.getType(), preference.getChannel()),
                    Boolean.TRUE.equals(preference.getEnabled()));
        }

        List<NotificationPreferenceDto> matrix = new ArrayList<>();
        for (NotificationType type : NotificationType.values()) {
            for (NotificationChannel channel : NotificationChannel.values()) {
                Boolean enabled = stored.get(key(type, channel));
                matrix.add(NotificationPreferenceDto.builder()
                        .type(type)
                        .channel(channel)
                        .enabled(enabled != null ? enabled : defaultEnabled(channel))
                        .build());
            }
        }

        return NotificationPreferencesResponse.builder()
                .preferences(matrix)
                .types(List.of(NotificationType.values()))
                .channels(List.of(NotificationChannel.values()))
                .build();
    }

    @Transactional
    public NotificationPreferencesResponse updatePreferences(
            Long userId, UpdateNotificationPreferencesRequest request) {
        featureFlags.requireEnabled(FeatureFlag.NOTIFICATIONS);
        if (request == null || request.getPreferences() == null
                || request.getPreferences().isEmpty()) {
            throw new BadRequestException("Список настроек не может быть пустым");
        }

        LocalDateTime now = LocalDateTime.now();
        for (UpdateNotificationPreferencesRequest.PreferenceUpdate update
                : request.getPreferences()) {
            if (update.getType() == null || update.getChannel() == null
                    || update.getEnabled() == null) {
                throw new BadRequestException(
                        "Для каждой настройки обязательны type, channel и enabled");
            }
            preferenceRepository.upsert(
                    userId,
                    update.getType().name(),
                    update.getChannel().name(),
                    update.getEnabled(),
                    now);
        }
        return getPreferences(userId);
    }

    // ============================================================
    // Helpers
    // ============================================================

    private boolean isChannelEnabled(Long userId,
                                     NotificationType type,
                                     NotificationChannel channel) {
        return preferenceRepository.findByUserIdAndTypeAndChannel(userId, type, channel)
                .map(preference -> Boolean.TRUE.equals(preference.getEnabled()))
                .orElseGet(() -> defaultEnabled(channel));
    }

    private boolean defaultEnabled(NotificationChannel channel) {
        return channel == NotificationChannel.IN_APP;
    }

    private String key(NotificationType type, NotificationChannel channel) {
        return type.name() + "|" + channel.name();
    }

    private void validate(NotificationCommand command) {
        if (command == null || command.userId() == null || command.type() == null) {
            throw new IllegalArgumentException("Уведомление требует user_id и type");
        }
        if (command.dedupeKey() != null && command.dedupeKey().length() > 200) {
            throw new IllegalArgumentException("dedupe_key не может быть длиннее 200 символов");
        }
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сериализовать payload уведомления", e);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private NotificationDto toDto(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .type(notification.getType())
                .title(notification.getTitle())
                .body(notification.getBody())
                .payload(notification.getPayload() == null ? Map.of() : notification.getPayload())
                .read(notification.getReadAt() != null)
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
