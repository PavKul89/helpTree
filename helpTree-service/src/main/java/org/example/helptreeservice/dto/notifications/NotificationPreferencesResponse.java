package org.example.helptreeservice.dto.notifications;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.NotificationChannel;
import org.example.helptreeservice.enums.NotificationType;

import java.util.List;

/** Full effective matrix plus the enumerations the client may render. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(name = "NotificationPreferencesResponse",
        description = "Full effective notification matrix plus the enumerations the client may render")
public class NotificationPreferencesResponse {

    private List<NotificationPreferenceDto> preferences;
    private List<NotificationType> types;
    private List<NotificationChannel> channels;
}
