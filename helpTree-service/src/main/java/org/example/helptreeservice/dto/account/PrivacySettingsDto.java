package org.example.helptreeservice.dto.account;

import lombok.Builder;
import lombok.Data;
import org.example.helptreeservice.enums.ProfileVisibility;

import java.time.LocalDateTime;

@Data
@Builder
public class PrivacySettingsDto {
    private ProfileVisibility profileVisibility;
    private Boolean showCity;
    private Boolean showEmail;
    private Boolean showPhone;
    private Boolean allowDirectMessages;
    private Boolean searchable;
    private LocalDateTime updatedAt;
}
