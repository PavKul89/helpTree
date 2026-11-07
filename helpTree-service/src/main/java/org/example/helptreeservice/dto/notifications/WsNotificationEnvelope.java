package org.example.helptreeservice.dto.notifications;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Payload pushed to {@code /user/queue/notifications}. {@code unreadCount} lets
 * the client update its badge without an extra round-trip; the client still
 * deduplicates by {@code notification.id} because the REST page and the
 * realtime push can race.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WsNotificationEnvelope {

    private String type;
    private NotificationDto notification;
    private long unreadCount;
    private LocalDateTime timestamp;

    public static WsNotificationEnvelope of(NotificationDto notification, long unreadCount) {
        return WsNotificationEnvelope.builder()
                .type("NOTIFICATION")
                .notification(notification)
                .unreadCount(unreadCount)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
