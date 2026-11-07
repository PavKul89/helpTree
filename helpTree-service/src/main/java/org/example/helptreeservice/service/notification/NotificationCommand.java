package org.example.helptreeservice.service.notification;

import org.example.helptreeservice.enums.NotificationType;

import java.util.Map;

/**
 * Internal creation command. {@code dedupeKey} is deterministic for a business
 * fact (e.g. {@code help:42:confirmed:helper}) and makes the write idempotent.
 */
public record NotificationCommand(
        Long userId,
        NotificationType type,
        String title,
        String body,
        Map<String, Object> payload,
        String dedupeKey) {

    public static NotificationCommand of(Long userId,
                                         NotificationType type,
                                         String title,
                                         String body,
                                         Map<String, Object> payload,
                                         String dedupeKey) {
        return new NotificationCommand(userId, type, title, body, payload, dedupeKey);
    }
}
