package org.example.helptreeservice.service.notification;

import org.example.helptreeservice.enums.NotificationChannel;

import java.util.Map;

/**
 * Immutable snapshot handed to a transport. The recipient is resolved at
 * delivery time from the current user row, so a changed email address or a newly
 * linked Telegram chat is used instead of a stale copy from creation time.
 */
public record NotificationDelivery(
        Long notificationId,
        Long userId,
        NotificationChannel channel,
        String recipient,
        String title,
        String body,
        Map<String, Object> payload) {
}
