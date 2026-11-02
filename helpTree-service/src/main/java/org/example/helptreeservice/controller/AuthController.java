package org.example.helptreeservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.auth.AuthResponse;
import org.example.helptreeservice.dto.auth.ChangePasswordRequest;
import org.example.helptreeservice.dto.auth.EmailChangeConfirmRequest;
import org.example.helptreeservice.dto.auth.EmailChangeRequest;
import org.example.helptreeservice.dto.auth.EmailVerificationConfirmRequest;
import org.example.helptreeservice.dto.auth.LoginRequest;
import org.example.helptreeservice.dto.auth.LogoutRequest;
import org.example.helptreeservice.dto.auth.PasswordResetConfirmRequest;
import org.example.helptreeservice.dto.auth.PasswordResetRequest;
import org.example.helptreeservice.dto.auth.RefreshTokenRequest;
import org.example.helptreeservice.dto.auth.SessionDto;
import org.example.helptreeservice.dto.users.CreateUserRequest;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.security.ClientInfoResolver;
import org.example.helptreeservice.service.AuthService;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.AuthorizationService.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthorizationService authorizationService;
    private final ClientInfoResolver clientInfoResolver;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody CreateUserRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(
                request,
                clientInfoResolver.resolveIp(httpRequest),
                clientInfoResolver.resolveUserAgent(httpRequest));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                HttpServletRequest httpRequest) {
        AuthResponse response = authService.refresh(
                request.getRefreshToken(),
                clientInfoResolver.resolveIp(httpRequest),
                clientInfoResolver.resolveUserAgent(httpRequest));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request) {
        UserContext user = requireCurrentUser();
        UUID sessionId = requireCurrentSession();
        authService.logout(user.getUserId(), sessionId,
                request != null ? request.getRefreshToken() : null);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll() {
        UserContext user = requireCurrentUser();
        authService.logoutAll(user.getUserId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionDto>> listSessions() {
        UserContext user = requireCurrentUser();
        return ResponseEntity.ok(authService.listSessions(user.getUserId(), requireCurrentSession()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId) {
        UserContext user = requireCurrentUser();
        UUID parsed = parseSessionId(sessionId);
        authService.revokeSession(user.getUserId(), parsed);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/change")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        UserContext user = requireCurrentUser();
        authService.changePassword(user.getUserId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/reset/request")
    public ResponseEntity<Map<String, String>> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request.getEmail());
        // Always the same answer, regardless of whether the address exists.
        return ResponseEntity.accepted().body(Map.of(
                "message", "Если аккаунт с таким email существует, письмо отправлено."));
    }

    @PostMapping("/password/reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request.getToken(), request.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email/verification/request")
    public ResponseEntity<Void> requestEmailVerification() {
        UserContext user = requireCurrentUser();
        authService.requestEmailVerification(user.getUserId());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/email/verification/confirm")
    public ResponseEntity<Void> confirmEmailVerification(
            @Valid @RequestBody EmailVerificationConfirmRequest request) {
        authService.confirmEmailVerification(request.getToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email/change")
    public ResponseEntity<Void> requestEmailChange(@Valid @RequestBody EmailChangeRequest request) {
        UserContext user = requireCurrentUser();
        authService.requestEmailChange(user.getUserId(), request.getNewEmail(), request.getPassword());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/email/change/confirm")
    public ResponseEntity<Void> confirmEmailChange(@Valid @RequestBody EmailChangeConfirmRequest request) {
        authService.confirmEmailChange(request.getToken());
        return ResponseEntity.noContent().build();
    }

    private UserContext requireCurrentUser() {
        UserContext user = authorizationService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }
        return user;
    }

    private UUID requireCurrentSession() {
        UUID sessionId = authorizationService.getCurrentSessionId();
        if (sessionId == null) {
            throw new UnauthorizedException("Сессия недействительна");
        }
        return sessionId;
    }

    private UUID parseSessionId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new org.example.helptreeservice.exception.BadRequestException("Некорректный ID сессии");
        }
    }
}
