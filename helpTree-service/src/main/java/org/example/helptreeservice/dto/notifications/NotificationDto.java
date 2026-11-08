package org.example.helptreeservice.dto.notifications;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.Map;

/** Public projection of a stored notification. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(name = "Notification", description = "Public projection of a stored notification")
public class NotificationDto {

    private Long id;
    private Long userId;
    private NotificationType type;
    private String title;
    private String body;
    private Map<String, Object> payload;
    private boolean read;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
}
