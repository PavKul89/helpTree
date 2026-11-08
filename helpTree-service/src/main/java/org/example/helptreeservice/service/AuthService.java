package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.auth.AuthResponse;
import org.example.helptreeservice.dto.auth.LoginRequest;
import org.example.helptreeservice.dto.auth.SessionDto;
import org.example.helptreeservice.dto.users.CreateUserRequest;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.exception.RateLimitExceededException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.metrics.HelpTreeMetrics;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.ratelimit.LoginProtectionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Orchestrates registration, login, token refresh and session self-service.
 * Authorization decisions always rely on the database state produced by
 * {@link AuthSessionService}; JWT claims are never trusted on their own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String INVALID_CREDENTIALS = "Неверный email или пароль";
    private static final String LOGIN_LOCKED =
            "Слишком много неудачных попыток входа. Попробуйте позже.";

    /**
     * Valid bcrypt hash used only to equalise response time for unknown accounts.
     * It never matches a real user password.
     */
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG";

    private final UserRepository userRepository;
    private final UserService userService;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final AuthSessionService authSessionService;
    private final AccountSecurityService accountSecurityService;
    private final LoginProtectionService loginProtectionService;
    private final HelpTreeMetrics metrics;

    @Transactional
    public AuthResponse register(CreateUserRequest request) {
        UserDto created = userService.createUser(request);
        User user = userService.getUserEntityById(created.getId());

        // A broken mail server must never make registration fail.
        runAfterCommit(() -> accountSecurityService.sendRegistrationVerification(user));

        AuthSessionService.IssuedTokens tokens = authSessionService.createSession(user, null, null);
        return toAuthResponse(tokens, null);
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ipAddress, String userAgent) {
        String email = normalizeEmail(request.getEmail());

        // The lockout key is (client IP, account). It is stored in the shared
        // database, so every instance sees the same state and the temporary ban
        // expires by itself.
        loginProtectionService.retryAfterSeconds(ipAddress, email).ifPresent(secondsLeft -> {
            log.warn("Попытка входа с временно заблокированного адреса: осталось {}с", secondsLeft);
            metrics.loginFailure("locked");
            throw new RateLimitExceededException(LOGIN_LOCKED, secondsLeft);
        });

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            // Spend comparable time to avoid leaking whether the account exists.
            passwordService.matches(request.getPassword(), DUMMY_PASSWORD_HASH);
            registerFailure(ipAddress, email, "unknown_account");
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        if (!passwordService.matches(request.getPassword(), user.getPassword())) {
            registerFailure(ipAddress, email, "bad_password");
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }

        if (Boolean.TRUE.equals(user.getDeleted())) {
            metrics.loginFailure("deleted");
            throw new UnauthorizedException("Аккаунт удалён");
        }

        LocalDateTime now = LocalDateTime.now();
        // Blocked accounts may still authenticate: the request filter only lets
        // them reach the wallet self-service endpoints, including the paid
        // ACCOUNT_UNBLOCK purchase. Every other protected path stays forbidden.
        if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(now)) {
            log.info("Пользователь {} вошёл в заблокированный аккаунт для разблокировки", user.getId());
        }

        loginProtectionService.recordSuccess(ipAddress, email);

        LocalDateTime previousLastLogin = user.getLastLogin();
        userService.updateLastLogin(user.getId());

        AuthSessionService.IssuedTokens tokens = authSessionService.createSession(user, ipAddress, userAgent);
        metrics.loginSuccess();
        log.info("Пользователь {} вошёл в систему", user.getId());
        return toAuthResponse(tokens, previousLastLogin);
    }

    /**
     * Deliberately not transactional: {@link AuthSessionService#rotateRefreshToken}
     * owns the transaction and must be able to commit its replay-revocation writes
     * even though it responds with an {@link UnauthorizedException}. An outer
     * transaction here would roll those writes back.
     */
    public AuthResponse refresh(String rawRefreshToken, String ipAddress, String userAgent) {
        AuthSessionService.IssuedTokens tokens =
                authSessionService.rotateRefreshToken(rawRefreshToken, ipAddress, userAgent);
        return toAuthResponse(tokens, null);
    }

    /**
     * Ends only the session identified by the access token. An optional refresh
     * token is accepted as extra proof but must belong to the same session, so a
     * caller can never terminate somebody else's session through this endpoint.
     */
    @Transactional
    public void logout(Long userId, UUID currentSessionId, String refreshToken) {
        if (userId == null || currentSessionId == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            UUID tokenSessionId = authSessionService.findSessionIdByRawToken(refreshToken)
                    .orElseThrow(() -> new ForbiddenException("Refresh токен не принадлежит текущей сессии"));
            if (!tokenSessionId.equals(currentSessionId)) {
                throw new ForbiddenException("Refresh токен не принадлежит текущей сессии");
            }
        }

        authSessionService.revokeSession(userId, currentSessionId, "LOGOUT");
        log.info("Пользователь {} завершил текущую сессию", userId);
    }

    /**
     * Revokes every session of the authenticated user and increments tokenVersion,
     * so all previously issued access tokens stop working immediately.
     */
    @Transactional
    public void logoutAll(Long userId) {
        authSessionService.revokeAllSessionsForUser(userId, "LOGOUT_ALL");
        log.info("Пользователь {} завершил все сессии", userId);
    }

    @Transactional(readOnly = true)
    public List<SessionDto> listSessions(Long userId, UUID currentSessionId) {
        return authSessionService.listActiveSessions(userId, currentSessionId).stream()
                .map(info -> SessionDto.builder()
                        .id(info.id().toString())
                        .createdAt(info.createdAt())
                        .lastUsedAt(info.lastUsedAt())
                        .expiresAt(info.expiresAt())
                        .ipAddress(info.ipAddress())
                        .userAgent(info.userAgent())
                        .deviceName(info.deviceName())
                        .current(info.current())
                        .build())
                .toList();
    }

    @Transactional
    public void revokeSession(Long userId, UUID sessionId) {
        if (sessionId == null) {
            throw new BadRequestException("ID сессии обязателен");
        }
        authSessionService.revokeSession(userId, sessionId, "USER_REVOKED");
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        accountSecurityService.changePassword(userId, currentPassword, newPassword);
    }

    @Transactional
    public void requestPasswordReset(String email) {
        accountSecurityService.requestPasswordReset(normalizeEmail(email));
    }

    @Transactional
    public void confirmPasswordReset(String token, String newPassword) {
        accountSecurityService.confirmPasswordReset(token, newPassword);
    }

    @Transactional
    public void requestEmailVerification(Long userId) {
        accountSecurityService.requestEmailVerification(userId);
    }

    @Transactional
    public void confirmEmailVerification(String token) {
        accountSecurityService.confirmEmailVerification(token);
    }

    @Transactional
    public void requestEmailChange(Long userId, String newEmail, String password) {
        accountSecurityService.requestEmailChange(userId, newEmail, password);
    }

    @Transactional
    public void confirmEmailChange(String token) {
        accountSecurityService.confirmEmailChange(token);
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(Long userId) {
        return userService.getUserById(userId);
    }

    @Transactional(readOnly = true)
    public AuthSessionService.WsTicket issueWsTicket(Long userId, UUID sessionId) {
        return authSessionService.issueWsTicket(userId, sessionId);
    }

    private AuthResponse toAuthResponse(AuthSessionService.IssuedTokens tokens,
                                        LocalDateTime previousLastLogin) {
        AuthResponse response = new AuthResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                tokens.userId(),
                tokens.email(),
                tokens.role(),
                jwtService.getExpirationTime());
        response.setSessionId(tokens.sessionId().toString());
        response.setEmailVerified(tokens.emailVerified());
        response.setPreviousLastLogin(previousLastLogin);
        return response;
    }

    private void registerFailure(String ipAddress, String email, String reason) {
        loginProtectionService.recordFailure(ipAddress, email).ifPresent(secondsLeft -> {
            log.warn("Вход временно заблокирован после серии неудачных попыток");
            metrics.loginFailure("locked");
            throw new RateLimitExceededException(LOGIN_LOCKED, secondsLeft);
        });
        metrics.loginFailure(reason);
        log.warn("Неудачная попытка входа");
        throw new UnauthorizedException(INVALID_CREDENTIALS);
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            throw new BadRequestException("Email обязателен");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

}
