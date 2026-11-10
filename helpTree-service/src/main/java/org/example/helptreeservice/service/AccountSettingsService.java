package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.account.NotificationPreferencesDto;
import org.example.helptreeservice.dto.account.PrivacySettingsDto;
import org.example.helptreeservice.dto.account.UpdateNotificationPreferencesRequest;
import org.example.helptreeservice.dto.account.UpdatePrivacySettingsRequest;
import org.example.helptreeservice.entity.AccountNotificationPreference;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.entity.UserPrivacySettings;
import org.example.helptreeservice.enums.AdminAuditAction;
import org.example.helptreeservice.enums.DigestFrequency;
import org.example.helptreeservice.enums.ProfileVisibility;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.AccountNotificationPreferenceRepository;
import org.example.helptreeservice.repository.UserPrivacySettingsRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * Self-service account settings: notification delivery, profile privacy,
 * portable data export and irreversible anonymization. Defaults match the
 * pre-V50 behaviour and are returned without creating a row until the user
 * actually saves a change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSettingsService {

    private final UserRepository userRepository;
    private final AccountNotificationPreferenceRepository notificationPreferenceRepository;
    private final UserPrivacySettingsRepository privacySettingsRepository;
    private final UserDataExportService userDataExportService;
    private final UserService userService;
    private final PasswordService passwordService;
    private final AdminAuditService adminAuditService;
    private final PublicProfileCache publicProfileCache;

    @Transactional(readOnly = true)
    public NotificationPreferencesDto getNotificationPreferences(Long userId) {
        requireActiveUser(userId);
        return notificationPreferenceRepository.findById(userId)
                .map(this::toDto)
                .orElseGet(this::defaultNotifications);
    }

    @Transactional
    public NotificationPreferencesDto updateNotificationPreferences(
            Long userId, UpdateNotificationPreferencesRequest request) {
        requireActiveUser(userId);

        AccountNotificationPreference preferences = notificationPreferenceRepository.findById(userId)
                .orElseGet(() -> AccountNotificationPreference.builder()
                        .userId(userId)
                        .emailEnabled(true)
                        .pushEnabled(true)
                        .inAppEnabled(true)
                        .digestFrequency(DigestFrequency.INSTANT)
                        .build());

        if (request.getEmailEnabled() != null) {
            preferences.setEmailEnabled(request.getEmailEnabled());
        }
        if (request.getPushEnabled() != null) {
            preferences.setPushEnabled(request.getPushEnabled());
        }
        if (request.getInAppEnabled() != null) {
            preferences.setInAppEnabled(request.getInAppEnabled());
        }
        if (request.getDigestFrequency() != null) {
            preferences.setDigestFrequency(request.getDigestFrequency());
        }

        LocalTime start = request.getQuietHoursStart();
        LocalTime end = request.getQuietHoursEnd();
        if ((start == null) != (end == null)) {
            throw new BadRequestException(
                    "Тихие часы должны быть заданы парой: начало и конец");
        }
        if (start != null) {
            preferences.setQuietHoursStart(start);
            preferences.setQuietHoursEnd(end);
        }

        LocalDateTime now = LocalDateTime.now();
        if (preferences.getCreatedAt() == null) {
            preferences.setCreatedAt(now);
        }
        preferences.setUpdatedAt(now);

        return toDto(notificationPreferenceRepository.save(preferences));
    }

    @Transactional(readOnly = true)
    public PrivacySettingsDto getPrivacySettings(Long userId) {
        requireActiveUser(userId);
        return privacySettingsRepository.findById(userId)
                .map(this::toDto)
                .orElseGet(this::defaultPrivacy);
    }

    @Transactional
    public PrivacySettingsDto updatePrivacySettings(
            Long userId, UpdatePrivacySettingsRequest request) {
        requireActiveUser(userId);

        UserPrivacySettings settings = privacySettingsRepository.findById(userId)
                .orElseGet(() -> UserPrivacySettings.builder()
                        .userId(userId)
                        .profileVisibility(ProfileVisibility.REGISTERED)
                        .showCity(true)
                        .showEmail(false)
                        .showPhone(false)
                        .showOnlineStatus(true)
                        .allowDirectMessages(true)
                        .searchable(true)
                        .build());

        if (request.getProfileVisibility() != null) {
            settings.setProfileVisibility(request.getProfileVisibility());
        }
        if (request.getShowCity() != null) {
            settings.setShowCity(request.getShowCity());
        }
        if (request.getShowEmail() != null) {
            settings.setShowEmail(request.getShowEmail());
        }
        if (request.getShowPhone() != null) {
            settings.setShowPhone(request.getShowPhone());
        }
        if (request.getShowOnlineStatus() != null) {
            settings.setShowOnlineStatus(request.getShowOnlineStatus());
        }
        if (request.getAllowDirectMessages() != null) {
            settings.setAllowDirectMessages(request.getAllowDirectMessages());
        }
        if (request.getSearchable() != null) {
            settings.setSearchable(request.getSearchable());
        }

        LocalDateTime now = LocalDateTime.now();
        if (settings.getCreatedAt() == null) {
            settings.setCreatedAt(now);
        }
        settings.setUpdatedAt(now);

        UserPrivacySettings saved = privacySettingsRepository.save(settings);
        // Every public projection reads these flags; the cache must not keep
        // serving the old city/phone/online visibility.
        publicProfileCache.evictAfterCommit(userId);
        return toDto(saved);
    }

    @Transactional
    public Map<String, Object> exportData(Long userId) {
        User user = requireActiveUser(userId);
        Map<String, Object> document = userDataExportService.export(user);

        adminAuditService.record(null, AdminAuditAction.USER_DATA_EXPORTED,
                AdminAuditService.ENTITY_USER, userId,
                null, Map.of("format", "json", "scope", "SELF"),
                "Самостоятельный экспорт данных пользователем");
        return document;
    }

    /**
     * Anonymization is irreversible. Requiring the current password prevents a
     * stolen access token from destroying an account.
     */
    @Transactional
    public void anonymizeAccount(Long userId, String password, String reason) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new NotFoundException("Пользователь не найден");
        }
        if (password == null || !passwordService.matches(password, user.getPassword())) {
            throw new BadRequestException("Пароль указан неверно");
        }

        String auditedReason = reason == null || reason.isBlank()
                ? "Пользователь удалил аккаунт с анонимизацией"
                : reason.trim();

        userService.anonymizeUser(userId, auditedReason);
        adminAuditService.record(null, AdminAuditAction.USER_ANONYMIZED,
                AdminAuditService.ENTITY_USER, userId,
                Map.of("deleted", false), Map.of("anonymized", true),
                auditedReason);
        log.info("Пользователь {} анонимизировал свой аккаунт", userId);
    }

    private User requireActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new NotFoundException("Пользователь не найден");
        }
        return user;
    }

    private NotificationPreferencesDto toDto(AccountNotificationPreference preferences) {
        return NotificationPreferencesDto.builder()
                .emailEnabled(preferences.getEmailEnabled())
                .pushEnabled(preferences.getPushEnabled())
                .inAppEnabled(preferences.getInAppEnabled())
                .digestFrequency(preferences.getDigestFrequency())
                .quietHoursStart(preferences.getQuietHoursStart())
                .quietHoursEnd(preferences.getQuietHoursEnd())
                .updatedAt(preferences.getUpdatedAt())
                .build();
    }

    private PrivacySettingsDto toDto(UserPrivacySettings settings) {
        return PrivacySettingsDto.builder()
                .profileVisibility(settings.getProfileVisibility())
                .showCity(settings.getShowCity())
                .showEmail(settings.getShowEmail())
                .showPhone(settings.getShowPhone())
                .showOnlineStatus(settings.getShowOnlineStatus())
                .allowDirectMessages(settings.getAllowDirectMessages())
                .searchable(settings.getSearchable())
                .updatedAt(settings.getUpdatedAt())
                .build();
    }

    private NotificationPreferencesDto defaultNotifications() {
        return NotificationPreferencesDto.builder()
                .emailEnabled(true)
                .pushEnabled(true)
                .inAppEnabled(true)
                .digestFrequency(DigestFrequency.INSTANT)
                .build();
    }

    private PrivacySettingsDto defaultPrivacy() {
        return PrivacySettingsDto.builder()
                .profileVisibility(ProfileVisibility.REGISTERED)
                .showCity(true)
                .showEmail(false)
                .showPhone(false)
                .showOnlineStatus(true)
                .allowDirectMessages(true)
                .searchable(true)
                .build();
    }
}
