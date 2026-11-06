package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.account.NotificationPreferencesDto;
import org.example.helptreeservice.dto.account.PrivacySettingsDto;
import org.example.helptreeservice.dto.account.UpdateNotificationPreferencesRequest;
import org.example.helptreeservice.dto.account.UpdatePrivacySettingsRequest;
import org.example.helptreeservice.entity.NotificationPreference;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.entity.UserPrivacySettings;
import org.example.helptreeservice.enums.AdminAuditAction;
import org.example.helptreeservice.enums.DigestFrequency;
import org.example.helptreeservice.enums.ProfileVisibility;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.NotificationPreferenceRepository;
import org.example.helptreeservice.repository.UserPrivacySettingsRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountSettingsServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private NotificationPreferenceRepository notificationPreferenceRepository;
    @Mock private UserPrivacySettingsRepository privacySettingsRepository;
    @Mock private UserDataExportService userDataExportService;
    @Mock private UserService userService;
    @Mock private PasswordService passwordService;
    @Mock private AdminAuditService adminAuditService;

    @InjectMocks private AccountSettingsService service;

    private static final Long USER_ID = 5L;

    private User activeUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("user@test.com");
        user.setPassword("encoded");
        user.setRole(Role.USER);
        user.setDeleted(false);
        return user;
    }

    // ---------------------------------------------------------------
    // Notifications
    // ---------------------------------------------------------------

    @Test
    void getNotificationsShouldReturnDefaultsWithoutCreatingRow() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(notificationPreferenceRepository.findById(USER_ID)).thenReturn(Optional.empty());

        NotificationPreferencesDto result = service.getNotificationPreferences(USER_ID);

        assertTrue(result.getEmailEnabled());
        assertTrue(result.getPushEnabled());
        assertTrue(result.getInAppEnabled());
        assertEquals(DigestFrequency.INSTANT, result.getDigestFrequency());
        assertNull(result.getQuietHoursStart());
        verify(notificationPreferenceRepository, never()).save(any());
    }

    @Test
    void updateNotificationsShouldCreateRowWithDefaultsAndApplyPatch() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(notificationPreferenceRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(notificationPreferenceRepository.save(any(NotificationPreference.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdateNotificationPreferencesRequest request = new UpdateNotificationPreferencesRequest();
        request.setEmailEnabled(false);
        request.setDigestFrequency(DigestFrequency.WEEKLY);
        request.setQuietHoursStart(LocalTime.of(22, 0));
        request.setQuietHoursEnd(LocalTime.of(7, 0));

        NotificationPreferencesDto result =
                service.updateNotificationPreferences(USER_ID, request);

        assertFalse(result.getEmailEnabled());
        assertTrue(result.getPushEnabled());
        assertEquals(DigestFrequency.WEEKLY, result.getDigestFrequency());
        assertEquals(LocalTime.of(22, 0), result.getQuietHoursStart());

        ArgumentCaptor<NotificationPreference> captor =
                ArgumentCaptor.forClass(NotificationPreference.class);
        verify(notificationPreferenceRepository).save(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUserId());
        assertNotNull(captor.getValue().getCreatedAt());
    }

    @Test
    void updateNotificationsShouldRejectHalfConfiguredQuietHours() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(notificationPreferenceRepository.findById(USER_ID)).thenReturn(Optional.empty());

        UpdateNotificationPreferencesRequest request = new UpdateNotificationPreferencesRequest();
        request.setQuietHoursStart(LocalTime.of(22, 0));

        assertThrows(BadRequestException.class,
                () -> service.updateNotificationPreferences(USER_ID, request));

        verify(notificationPreferenceRepository, never()).save(any());
    }

    @Test
    void notificationSettingsShouldRejectDeletedUser() {
        User deleted = activeUser();
        deleted.setDeleted(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(deleted));

        assertThrows(NotFoundException.class,
                () -> service.getNotificationPreferences(USER_ID));
    }

    // ---------------------------------------------------------------
    // Privacy
    // ---------------------------------------------------------------

    @Test
    void getPrivacyShouldReturnSafeDefaults() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(privacySettingsRepository.findById(USER_ID)).thenReturn(Optional.empty());

        PrivacySettingsDto result = service.getPrivacySettings(USER_ID);

        assertEquals(ProfileVisibility.REGISTERED, result.getProfileVisibility());
        assertTrue(result.getShowCity());
        assertFalse(result.getShowEmail());
        assertFalse(result.getShowPhone());
        assertTrue(result.getAllowDirectMessages());
        assertTrue(result.getSearchable());
        verify(privacySettingsRepository, never()).save(any());
    }

    @Test
    void updatePrivacyShouldCreateRowAndApplyOnlyProvidedFields() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(activeUser()));
        when(privacySettingsRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(privacySettingsRepository.save(any(UserPrivacySettings.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UpdatePrivacySettingsRequest request = new UpdatePrivacySettingsRequest();
        request.setProfileVisibility(ProfileVisibility.PRIVATE);
        request.setShowCity(false);

        PrivacySettingsDto result = service.updatePrivacySettings(USER_ID, request);

        assertEquals(ProfileVisibility.PRIVATE, result.getProfileVisibility());
        assertFalse(result.getShowCity());
        assertFalse(result.getShowEmail());
        assertTrue(result.getSearchable());

        ArgumentCaptor<UserPrivacySettings> captor =
                ArgumentCaptor.forClass(UserPrivacySettings.class);
        verify(privacySettingsRepository).save(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUserId());
        assertNotNull(captor.getValue().getCreatedAt());
    }

    // ---------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------

    @Test
    void exportShouldReturnDocumentAndWriteSelfAudit() {
        User user = activeUser();
        Map<String, Object> document = Map.of("user", Map.of("id", USER_ID));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(userDataExportService.export(user)).thenReturn(document);

        Map<String, Object> result = service.exportData(USER_ID);

        assertEquals(document, result);
        verify(adminAuditService).record(isNull(), eq(AdminAuditAction.USER_DATA_EXPORTED),
                eq(AdminAuditService.ENTITY_USER), eq(USER_ID), isNull(), anyMap(), anyString());
    }

    @Test
    void exportShouldRejectDeletedUser() {
        User deleted = activeUser();
        deleted.setDeleted(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(deleted));

        assertThrows(NotFoundException.class, () -> service.exportData(USER_ID));
        verifyNoInteractions(userDataExportService, adminAuditService);
    }

    // ---------------------------------------------------------------
    // Anonymization
    // ---------------------------------------------------------------

    @Test
    void anonymizeShouldRequireTheCurrentPassword() {
        User user = activeUser();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(passwordService.matches("wrong", "encoded")).thenReturn(false);

        assertThrows(BadRequestException.class,
                () -> service.anonymizeAccount(USER_ID, "wrong", null));

        verify(userService, never()).anonymizeUser(anyLong(), anyString());
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void anonymizeShouldRejectDeletedAccount() {
        User deleted = activeUser();
        deleted.setDeleted(true);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(deleted));

        assertThrows(NotFoundException.class,
                () -> service.anonymizeAccount(USER_ID, "password", null));

        verifyNoInteractions(userService, adminAuditService);
    }

    @Test
    void anonymizeShouldDelegateAndAuditWithoutAdminId() {
        User user = activeUser();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(passwordService.matches("password", "encoded")).thenReturn(true);

        service.anonymizeAccount(USER_ID, "password", "  ");

        verify(userService).anonymizeUser(USER_ID,
                "Пользователь удалил аккаунт с анонимизацией");
        verify(adminAuditService).record(isNull(), eq(AdminAuditAction.USER_ANONYMIZED),
                eq(AdminAuditService.ENTITY_USER), eq(USER_ID), anyMap(), anyMap(),
                eq("Пользователь удалил аккаунт с анонимизацией"));
    }

    @Test
    void anonymizeShouldKeepExplicitReason() {
        User user = activeUser();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        when(passwordService.matches("password", "encoded")).thenReturn(true);

        service.anonymizeAccount(USER_ID, "password", "moved to another service");

        verify(userService).anonymizeUser(USER_ID, "moved to another service");
    }
}
