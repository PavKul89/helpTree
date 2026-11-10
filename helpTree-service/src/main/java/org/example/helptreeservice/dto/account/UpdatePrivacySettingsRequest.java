package org.example.helptreeservice.dto.account;

import lombok.Data;
import org.example.helptreeservice.enums.ProfileVisibility;

@Data
public class UpdatePrivacySettingsRequest {
    private ProfileVisibility profileVisibility;
    private Boolean showCity;
    private Boolean showEmail;
    private Boolean showPhone;
    private Boolean showOnlineStatus;
    private Boolean allowDirectMessages;
    private Boolean searchable;
}
