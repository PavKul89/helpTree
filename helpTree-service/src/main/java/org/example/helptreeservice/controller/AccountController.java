package org.example.helptreeservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.dto.account.AccountAnonymizeRequest;
import org.example.helptreeservice.dto.account.NotificationPreferencesDto;
import org.example.helptreeservice.dto.account.PrivacySettingsDto;
import org.example.helptreeservice.dto.account.UpdateNotificationPreferencesRequest;
import org.example.helptreeservice.dto.account.UpdatePrivacySettingsRequest;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AccountSettingsService;
import org.example.helptreeservice.service.AuthorizationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Self-service account settings. Every endpoint derives the user id from the
 * validated access token; a body or path parameter can never target another
 * account.
 */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@Tag(name = "Account", description = "Самообслуживание аккаунта")
public class AccountController {

    private final AccountSettingsService accountSettingsService;
    private final AuthorizationService authService;

    @GetMapping("/notifications")
    @Operation(summary = "Настройки уведомлений текущего аккаунта")
    public ResponseEntity<NotificationPreferencesDto> getNotifications() {
        return ResponseEntity.ok(accountSettingsService.getNotificationPreferences(currentUserId()));
    }

    @PutMapping("/notifications")
    @Operation(summary = "Обновить настройки уведомлений аккаунта")
    public ResponseEntity<NotificationPreferencesDto> updateNotifications(
            @RequestBody UpdateNotificationPreferencesRequest request) {
        return ResponseEntity.ok(
                accountSettingsService.updateNotificationPreferences(currentUserId(), request));
    }

    @GetMapping("/privacy")
    @Operation(summary = "Настройки приватности аккаунта")
    public ResponseEntity<PrivacySettingsDto> getPrivacy() {
        return ResponseEntity.ok(accountSettingsService.getPrivacySettings(currentUserId()));
    }

    @PutMapping("/privacy")
    @Operation(summary = "Обновить настройки приватности аккаунта")
    public ResponseEntity<PrivacySettingsDto> updatePrivacy(
            @RequestBody UpdatePrivacySettingsRequest request) {
        return ResponseEntity.ok(
                accountSettingsService.updatePrivacySettings(currentUserId(), request));
    }

    @GetMapping("/export")
    @Operation(summary = "Экспорт данных аккаунта")
    public ResponseEntity<Map<String, Object>> exportData() {
        return ResponseEntity.ok(accountSettingsService.exportData(currentUserId()));
    }

    @PostMapping("/anonymize")
    @Operation(summary = "Анонимизировать собственный аккаунт",
            description = "Необратимо; требует текущий пароль и причину.")
    public ResponseEntity<Void> anonymize(@Valid @RequestBody AccountAnonymizeRequest request) {
        accountSettingsService.anonymizeAccount(
                currentUserId(), request.getPassword(), request.getReason());
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId() {
        AuthorizationService.UserContext current = authService.getCurrentUser();
        if (current == null || current.getUserId() == null) {
            throw new UnauthorizedException("Вы не авторизованы");
        }
        return current.getUserId();
    }
}
