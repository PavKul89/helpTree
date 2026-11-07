package org.example.helptreeservice.dto.notifications;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/** Partial update: only the listed (type, channel) pairs are changed. */
@Data
public class UpdateNotificationPreferencesRequest {

    @Valid
    @NotEmpty(message = "Список настроек не может быть пустым")
    private List<PreferenceUpdate> preferences;

    @Data
    public static class PreferenceUpdate {

        private org.example.helptreeservice.enums.NotificationType type;
        private org.example.helptreeservice.enums.NotificationChannel channel;
        private Boolean enabled;
    }
}
