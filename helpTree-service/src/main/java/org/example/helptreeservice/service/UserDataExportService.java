package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.entity.NotificationPreference;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.entity.UserPrivacySettings;
import org.example.helptreeservice.enums.DigestFrequency;
import org.example.helptreeservice.enums.ProfileVisibility;
import org.example.helptreeservice.repository.NotificationPreferenceRepository;
import org.example.helptreeservice.repository.UserPrivacySettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the portable JSON snapshot used by both the self-service export and the
 * administrative export endpoint. Passwords, tokens and session secrets are
 * deliberately absent.
 */
@Service
@RequiredArgsConstructor
public class UserDataExportService {

    private final UserPrivacySettingsRepository privacySettingsRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> export(User user) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("exportedAt", LocalDateTime.now());
        document.put("user", userFields(user));
        document.put("privacy", privacyFields(user.getId()));
        document.put("notifications", notificationFields(user.getId()));
        return document;
    }

    public Map<String, Object> userFields(User user) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("id", user.getId());
        fields.put("name", user.getName());
        fields.put("email", user.getEmail());
        fields.put("phone", user.getPhone());
        fields.put("city", user.getCity());
        fields.put("latitude", user.getLatitude());
        fields.put("longitude", user.getLongitude());
        fields.put("role", user.getRole());
        fields.put("status", user.getStatus());
        fields.put("rating", user.getRating());
        fields.put("helpedCount", user.getHelpedCount());
        fields.put("debtCount", user.getDebtCount());
        fields.put("helpCoins", user.getHelpCoins());
        fields.put("birthDate", user.getBirthDate());
        fields.put("createdAt", user.getCreatedAt());
        fields.put("updatedAt", user.getUpdatedAt());
        fields.put("lastLogin", user.getLastLogin());
        fields.put("vipUntil", user.getVipUntil());
        fields.put("emailVerified", user.getEmailVerified());
        fields.put("deleted", user.getDeleted());
        fields.put("deletedAt", user.getDeletedAt());
        fields.put("anonymizedAt", user.getAnonymizedAt());
        fields.put("favoritePostIds", user.getFavoritePostIds());
        return fields;
    }

    private Map<String, Object> privacyFields(Long userId) {
        UserPrivacySettings settings = privacySettingsRepository.findById(userId).orElse(null);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("profileVisibility", settings != null
                ? settings.getProfileVisibility() : ProfileVisibility.REGISTERED);
        fields.put("showCity", settings != null ? settings.getShowCity() : true);
        fields.put("showEmail", settings != null ? settings.getShowEmail() : false);
        fields.put("showPhone", settings != null ? settings.getShowPhone() : false);
        fields.put("allowDirectMessages", settings != null ? settings.getAllowDirectMessages() : true);
        fields.put("searchable", settings != null ? settings.getSearchable() : true);
        return fields;
    }

    private Map<String, Object> notificationFields(Long userId) {
        NotificationPreference preferences = notificationPreferenceRepository.findById(userId).orElse(null);
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("emailEnabled", preferences != null ? preferences.getEmailEnabled() : true);
        fields.put("pushEnabled", preferences != null ? preferences.getPushEnabled() : true);
        fields.put("inAppEnabled", preferences != null ? preferences.getInAppEnabled() : true);
        fields.put("digestFrequency", preferences != null
                ? preferences.getDigestFrequency() : DigestFrequency.INSTANT);
        fields.put("quietHoursStart", preferences != null ? preferences.getQuietHoursStart() : null);
        fields.put("quietHoursEnd", preferences != null ? preferences.getQuietHoursEnd() : null);
        return fields;
    }
}
