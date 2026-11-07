package org.example.helptreeservice.service.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.notifications.NotificationDto;
import org.example.helptreeservice.dto.notifications.WsNotificationEnvelope;
import org.example.helptreeservice.repository.NotificationRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pushes the canonical notification to the private STOMP queue.
 *
 * <p>The push is executed only after the creating transaction commits. It opens
 * a new read-only transaction so the unread count observes the committed row,
 * and never participates in the business transaction: a broker problem must not
 * roll back a help confirmation or a report decision.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationRealtimeService {

    /** User destination suffix; the broker resolves it to /user/{id}/queue/notifications. */
    public static final String NOTIFICATION_QUEUE = "/queue/notifications";

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void push(Long userId, NotificationDto notification) {
        if (userId == null || notification == null) {
            return;
        }
        long unreadCount = notificationRepository.countByUserIdAndReadAtIsNull(userId);
        WsNotificationEnvelope envelope = WsNotificationEnvelope.of(notification, unreadCount);
        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(), NOTIFICATION_QUEUE, envelope);
        } catch (RuntimeException e) {
            // The REST page remains the source of truth; a lost push is recovered
            // by the client on the next load.
            log.warn("Не удалось отправить WebSocket-уведомление {} пользователю {}: {}",
                    notification.getId(), userId, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }
}
