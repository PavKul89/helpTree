package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.auth.AuthResponse;
import org.example.helptreeservice.dto.auth.LoginRequest;
import org.example.helptreeservice.dto.auth.SessionDto;
import org.example.helptreeservice.dto.users.CreateUserRequest;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.exception.AccountBlockedException;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates registration, login, token refresh and session self-service.
 * Authorization decisions always rely on the database state produced by
 * {@link AuthSessionService}; JWT claims are never trusted on their own.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000L;

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

    private final ConcurrentHashMap<String, FailedLoginTracker> failedAttempts = new ConcurrentHashMap<>();

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
        FailedLoginTracker tracker = failedAttempts.computeIfAbsent(email, key -> new FailedLoginTracker());

        if (tracker.isLocked()) {
            long secondsLeft = tracker.secondsUntilUnlock();
            log.warn("Попытка входа с временно заблокированного email: осталось {}с", secondsLeft);
            throw new BadRequestException(
                    "Слишком много неудачных попыток. Попробуйте через " + secondsLeft + " секунд.");
        }

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            // Spend comparable time to avoid leaking whether the account exists.
            passwordService.matches(request.getPassword(), DUMMY_PASSWORD_HASH);
            registerFailure(tracker, email);
            throw new UnauthorizedException("Неверный email или пароль");
        }

        if (!passwordService.matches(request.getPassword(), user.getPassword())) {
            registerFailure(tracker, email);
            throw new UnauthorizedException("Неверный email или пароль");
        }

        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new UnauthorizedException("Аккаунт удалён");
        }

        LocalDateTime now = LocalDateTime.now();
        if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(now)) {
            throw new AccountBlockedException("Аккаунт заблокирован до " + user.getBlockedUntil());
        }

        failedAttempts.remove(email);

        LocalDateTime previousLastLogin = user.getLastLogin();
        userService.updateLastLogin(user.getId());

        AuthSessionService.IssuedTokens tokens = authSessionService.createSession(user, ipAddress, userAgent);
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

    @Scheduled(cron = "0 */10 * * * *")
    public void cleanupFailedLoginTrackers() {
        Instant now = Instant.now();
        failedAttempts.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
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

    private void registerFailure(FailedLoginTracker tracker, String email) {
        int count = tracker.registerFailure();
        if (count >= MAX_FAILED_ATTEMPTS) {
            tracker.lock(LOCKOUT_DURATION_MS);
            log.warn("Вход временно заблокирован после {} неудачных попыток", count);
            throw new BadRequestException(
                    "Слишком много неудачных попыток. Попробуйте через 15 минут.");
        }
        log.warn("Неудачная попытка входа: попытка {}/{}", count, MAX_FAILED_ATTEMPTS);
        throw new UnauthorizedException("Неверный email или пароль");
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

    private static final class FailedLoginTracker {
        private int count;
        private Instant lockedUntil;
        private Instant lastFailure;

        synchronized boolean isLocked() {
            return lockedUntil != null && Instant.now().isBefore(lockedUntil);
        }

        synchronized long secondsUntilUnlock() {
            if (lockedUntil == null) {
                return 0;
            }
            return Math.max(0, Duration.between(Instant.now(), lockedUntil).getSeconds());
        }

        synchronized int registerFailure() {
            count++;
            lastFailure = Instant.now();
            return count;
        }

        synchronized void lock(long durationMs) {
            lockedUntil = Instant.now().plusMillis(durationMs);
        }

        synchronized boolean isExpired(Instant now) {
            if (lockedUntil != null && lockedUntil.isAfter(now)) {
                return false;
            }
            return lastFailure == null
                    || lastFailure.isBefore(now.minusMillis(LOCKOUT_DURATION_MS));
        }
    }
}
