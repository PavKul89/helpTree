package org.example.helptreeservice.dto.account;

import lombok.Data;
import org.example.helptreeservice.enums.DigestFrequency;

import java.time.LocalTime;

@Data
public class UpdateNotificationPreferencesRequest {
    private Boolean emailEnabled;
    private Boolean pushEnabled;
    private Boolean inAppEnabled;
    private DigestFrequency digestFrequency;
    private LocalTime quietHoursStart;
    private LocalTime quietHoursEnd;
}
