package org.example.helptreeservice.dto.account;

import lombok.Builder;
import lombok.Data;
import org.example.helptreeservice.enums.DigestFrequency;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
public class NotificationPreferencesDto {
    private Boolean emailEnabled;
    private Boolean pushEnabled;
    private Boolean inAppEnabled;
    private DigestFrequency digestFrequency;
    private LocalTime quietHoursStart;
    private LocalTime quietHoursEnd;
    private LocalDateTime updatedAt;
}
