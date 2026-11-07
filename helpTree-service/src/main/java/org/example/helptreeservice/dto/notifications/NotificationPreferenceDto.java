package org.example.helptreeservice.dto.notifications;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.NotificationChannel;
import org.example.helptreeservice.enums.NotificationType;

import java.time.LocalDateTime;

/**
 * Effective setting for one (type, channel) pair. A pair without a stored row
 * is returned with its default: IN_APP enabled, EMAIL/TELEGRAM disabled.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreferenceDto {

    private NotificationType type;
    private NotificationChannel channel;
    private boolean enabled;
    private LocalDateTime updatedAt;
}
