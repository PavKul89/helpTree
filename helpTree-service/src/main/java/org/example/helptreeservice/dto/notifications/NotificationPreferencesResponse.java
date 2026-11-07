package org.example.helptreeservice.dto.notifications;

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
public class NotificationPreferencesResponse {

    private List<NotificationPreferenceDto> preferences;
    private List<NotificationType> types;
    private List<NotificationChannel> channels;
}
