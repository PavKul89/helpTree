package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.AuthConfig;
import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.entity.AuthSession;
import org.example.helptreeservice.entity.RefreshToken;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.exception.AccountBlockedException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.repository.AuthSessionRepository;
import org.example.helptreeservice.repository.RefreshTokenRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns login sessions, hashed refresh-token rotation and per-request access-token
 * validation. Authentication decisions are always based on current database state,
 * never on the role/claims embedded in an old JWT.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSessionService {

    private final AuthSessionRepository authSessionRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final TokenService tokenService;
    private final EconomyConfig economyConfig;
    private final AuthConfig authConfig;

    public record IssuedTokens(String accessToken,
                               String refreshToken,
                               UUID sessionId,
                               Long userId,
                               String email,
                               String role,
                               boolean emailVerified) {
    }

    public record AuthenticatedUser(Long userId,
                                    String email,
                                    String role,
                                    UUID sessionId,
                                    long tokenVersion) {
    }

    public record SessionInfo(UUID id,
                              LocalDateTime createdAt,
                              LocalDateTime lastUsedAt,
                              LocalDateTime expiresAt,
                              String ipAddress,
                              String userAgent,
                              String deviceName,
                              boolean current) {
    }

    public record WsTicket(String ticket, int expiresInSeconds) {
    }

    @Transactional
    public IssuedTokens createSession(User user, String ipAddress, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        // Debt-blocked accounts may sign in so they can reach the wallet
        // self-service endpoints; the request filter still denies everything
        // else until the block is paid off or expires.
        assertUserNotDeleted(user);

        AuthSession session = AuthSession.builder()
                .id(UUID.randomUUID())
                .user(user)
                .createdAt(now)
                .lastUsedAt(now)
                .expiresAt(now.plusDays(refreshTokenLifetimeDays()))
                .ipAddress(normalizeIp(ipAddress))
                .userAgent(truncate(userAgent, 512))
                .build();
        authSessionRepository.save(session);

        String rawRefreshToken = issueRefreshToken(user, session, now);
        String accessToken = issueAccessToken(user, session);

        log.info("Создана сессия {} для пользователя {}", session.getId(), user.getId());
        return new IssuedTokens(accessToken, rawRefreshToken, session.getId(), user.getId(),
                user.getEmail(), user.getRole().name(), Boolean.TRUE.equals(user.getEmailVerified()));
    }

    /**
     * Atomically rotates a refresh token. The session row is locked before the
     * token row (consistent lock order with revocation methods), so concurrent
     * requests cannot both consume the same token.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public IssuedTokens rotateRefreshToken(String rawRefreshToken, String ipAddress, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Refresh токен обязателен");
        }

        String tokenHash = tokenService.hash(rawRefreshToken);
        // Resolve only the session id first. Loading the token entity before taking
        // the session lock would leave a stale copy in the persistence context if a
        // concurrent request rotates the same token while we wait for the lock.
        UUID sessionId = refreshTokenRepository.findSessionIdByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Невалидный refresh токен"));

        AuthSession session = authSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new UnauthorizedException("Сессия недействительна"));
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Невалидный refresh токен"));

        if (!token.getSession().getId().equals(session.getId())) {
            throw new UnauthorizedException("Невалидный refresh токен");
        }

        User user = session.getUser();
        if (user == null || !user.getId().equals(token.getUser().getId())) {
            throw new UnauthorizedException("Невалидный refresh токен");
        }

        LocalDateTime now = LocalDateTime.now();

        // A revoked or already-used token means someone is replaying an old value.
        // Revoke the whole token family (the session) in that case.
        if (token.isRevoked()) {
            revokeForReplay(session, now);
            throw new UnauthorizedException("Refresh токен уже использован. Сессия завершена.");
        }

        if (!session.isActive(now)) {
            revokeToken(token, now, "SESSION_INACTIVE");
            throw new UnauthorizedException("Сессия недействительна");
        }

        if (token.isExpired(now)) {
            revokeToken(token, now, "EXPIRED");
            throw new UnauthorizedException("Refresh токен истёк");
        }

        assertUserNotDeleted(user);

        revokeToken(token, now, "ROTATED");
        session.setLastUsedAt(now);
        session.setExpiresAt(now.plusDays(refreshTokenLifetimeDays()));
        if (ipAddress != null && !ipAddress.isBlank()) {
            session.setIpAddress(normalizeIp(ipAddress));
        }
        if (userAgent != null && !userAgent.isBlank()) {
            session.setUserAgent(truncate(userAgent, 512));
        }

        String newRawRefreshToken = issueRefreshToken(user, session, now);
        String accessToken = issueAccessToken(user, session);

        log.info("Refresh токен ротирован для сессии {} пользователя {}", session.getId(), user.getId());
        return new IssuedTokens(accessToken, newRawRefreshToken, session.getId(), user.getId(),
                user.getEmail(), user.getRole().name(), Boolean.TRUE.equals(user.getEmailVerified()));
    }

    /**
     * Validates an access token against the current session and user row.
     */
    @Transactional
    public AuthenticatedUser validateAccessToken(Long userId,
                                                 String sessionIdValue,
                                                 Long tokenVersion,
                                                 String role) {
        return validateAccessToken(userId, sessionIdValue, tokenVersion, role, false);
    }

    /**
     * Same as {@link #validateAccessToken(Long, String, Long, String)} but lets
     * callers opt in to authenticating a debt-blocked account. The HTTP filter
     * uses this only for the wallet self-service paths that allow a blocked user
     * to inspect the wallet and purchase ACCOUNT_UNBLOCK; every other endpoint
     * still rejects blocked accounts.
     */
    @Transactional
    public AuthenticatedUser validateAccessToken(Long userId,
                                                 String sessionIdValue,
                                                 Long tokenVersion,
                                                 String role,
                                                 boolean allowBlocked) {
        if (userId == null || sessionIdValue == null || tokenVersion == null || role == null) {
            throw new UnauthorizedException("Невалидный токен");
        }

        UUID sessionId;
        try {
            sessionId = UUID.fromString(sessionIdValue);
        } catch (IllegalArgumentException e) {
            throw new UnauthorizedException("Невалидный токен");
        }

        LocalDateTime now = LocalDateTime.now();
        AuthSession session = authSessionRepository.findActiveById(sessionId, now)
                .orElseThrow(() -> new UnauthorizedException("Сессия недействительна"));

        User user = session.getUser();
        if (user == null || Boolean.TRUE.equals(user.getDeleted()) || !user.getId().equals(userId)) {
            throw new UnauthorizedException("Сессия недействительна");
        }

        if (!allowBlocked) {
            assertNotBlocked(user, now);
        }
        if (safeTokenVersion(user) != tokenVersion || !user.getRole().name().equals(role)) {
            throw new UnauthorizedException("Права доступа изменились. Войдите снова.");
        }

        touchSession(session, now);
        touchLastSeen(user, now);

        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole().name(),
                session.getId(), safeTokenVersion(user));
    }

    /**
     * Issues a short-lived, single-purpose ticket for the WebSocket handshake.
     * The ticket is bound to the caller's active session and is revalidated against
     * the database when the WebSocket upgrade is accepted.
     */
    @Transactional(readOnly = true)
    public WsTicket issueWsTicket(Long userId, UUID sessionId) {
        if (userId == null || sessionId == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }

        LocalDateTime now = LocalDateTime.now();
        AuthSession session = authSessionRepository.findActiveById(sessionId, now)
                .orElseThrow(() -> new UnauthorizedException("Сессия недействительна"));

        User user = session.getUser();
        if (user == null || !user.getId().equals(userId)) {
            throw new UnauthorizedException("Сессия недействительна");
        }
        assertUserCanAuthenticate(user, now);

        int ttlSeconds = Math.max(1, authConfig.getWsTicketSeconds());
        String ticket = jwtService.generateWsTicket(
                user.getId(),
                user.getEmail(),
                user.getRole().name(),
                session.getId().toString(),
                safeTokenVersion(user),
                ttlSeconds);
        return new WsTicket(ticket, ttlSeconds);
    }

    @Transactional
    public void revokeSession(Long userId, UUID sessionId, String reason) {
        AuthSession session = authSessionRepository.findByIdAndUserIdForUpdate(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Сессия не найдена"));
        revokeSessionInternal(session, reason, LocalDateTime.now());
        log.info("Сессия {} пользователя {} отозвана ({})", sessionId, userId, reason);
    }

    @Transactional
    public UUID revokeSessionByRefreshToken(String rawRefreshToken, String reason) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new UnauthorizedException("Refresh токен обязателен");
        }

        String tokenHash = tokenService.hash(rawRefreshToken);
        UUID sessionId = refreshTokenRepository.findSessionIdByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Невалидный refresh токен"));

        AuthSession session = authSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new UnauthorizedException("Сессия недействительна"));
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Невалидный refresh токен"));

        if (!token.getSession().getId().equals(session.getId())
                || !token.getUser().getId().equals(session.getUser().getId())) {
            throw new UnauthorizedException("Невалидный refresh токен");
        }

        revokeSessionInternal(session, reason, LocalDateTime.now());
        log.info("Сессия {} завершена по refresh токену ({})", session.getId(), reason);
        return session.getId();
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findSessionIdByRawToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByTokenHash(tokenService.hash(rawRefreshToken))
                .map(token -> token.getSession().getId());
    }

    /**
     * Revokes every session and increments tokenVersion, invalidating all access
     * tokens that were issued before this call.
     */
    @Transactional
    public void revokeAllSessionsForUser(Long userId, String reason) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        user.setTokenVersion(safeTokenVersion(user) + 1);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        LocalDateTime now = LocalDateTime.now();
        authSessionRepository.revokeAllActiveByUserId(userId, now, reason);
        refreshTokenRepository.revokeAllByUserId(userId, now, reason);

        log.info("Все сессии пользователя {} отозваны ({})", userId, reason);
    }

    @Transactional(readOnly = true)
    public List<SessionInfo> listActiveSessions(Long userId, UUID currentSessionId) {
        LocalDateTime now = LocalDateTime.now();
        return authSessionRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(userId, now)
                .stream()
                .map(session -> new SessionInfo(
                        session.getId(),
                        session.getCreatedAt(),
                        session.getLastUsedAt() != null ? session.getLastUsedAt() : session.getCreatedAt(),
                        session.getExpiresAt(),
                        session.getIpAddress(),
                        session.getUserAgent(),
                        describeDevice(session.getUserAgent()),
                        currentSessionId != null && currentSessionId.equals(session.getId())))
                .toList();
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime revokedCutoff = now.minusDays(30);
        int deleted = authSessionRepository.deleteExpiredOrOldRevoked(now, revokedCutoff);
        refreshTokenRepository.deleteExpiredBefore(now);
        if (deleted > 0) {
            log.info("Удалено {} истёкших/отозванных сессий", deleted);
        }
    }

    private String issueAccessToken(User user, AuthSession session) {
        return jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name(),
                session.getId().toString(), safeTokenVersion(user));
    }

    private String issueRefreshToken(User user, AuthSession session, LocalDateTime now) {
        String rawToken = tokenService.generate();
        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(tokenService.hash(rawToken))
                .session(session)
                .user(user)
                .createdAt(now)
                .expiresAt(session.getExpiresAt())
                .build();
        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    private void revokeForReplay(AuthSession session, LocalDateTime now) {
        log.warn("Обнаружен replay refresh токена для сессии {}. Отзываю всю семью токенов.",
                session.getId());
        revokeSessionInternal(session, "REFRESH_REPLAY", now);
    }

    private void revokeSessionInternal(AuthSession session, String reason, LocalDateTime now) {
        if (session.getRevokedAt() == null) {
            session.setRevokedAt(now);
            session.setRevokedReason(reason);
            authSessionRepository.save(session);
        }
        refreshTokenRepository.revokeAllBySessionId(session.getId(), now, reason);
    }

    private void revokeToken(RefreshToken token, LocalDateTime now, String reason) {
        token.setRevokedAt(now);
        token.setRevokedReason(reason);
        refreshTokenRepository.save(token);
    }

    private void touchSession(AuthSession session, LocalDateTime now) {
        LocalDateTime threshold = now.minusMinutes(Math.max(1, authConfig.getSessionTouchMinutes()));
        if (session.getLastUsedAt() == null || session.getLastUsedAt().isBefore(threshold)) {
            session.setLastUsedAt(now);
        }
    }

    /**
     * Persists the online indicator timestamp, throttled independently from the
     * session touch. A bulk update keeps the users row's {@code updated_at}
     * stable, so activity does not look like a profile change.
     */
    private void touchLastSeen(User user, LocalDateTime now) {
        LocalDateTime threshold = now.minusMinutes(Math.max(1, authConfig.getLastSeenTouchMinutes()));
        if (user.getLastSeenAt() == null || user.getLastSeenAt().isBefore(threshold)) {
            userRepository.touchLastSeen(user.getId(), now, threshold);
        }
    }

    private void assertUserCanAuthenticate(User user, LocalDateTime now) {
        assertUserNotDeleted(user);
        assertNotBlocked(user, now);
    }

    private void assertUserNotDeleted(User user) {
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            throw new UnauthorizedException("Аккаунт удалён");
        }
    }

    private void assertNotBlocked(User user, LocalDateTime now) {
        if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(now)) {
            throw new AccountBlockedException("Аккаунт заблокирован до " + user.getBlockedUntil());
        }
    }

    private long safeTokenVersion(User user) {
        return user.getTokenVersion() != null ? user.getTokenVersion() : 0L;
    }

    private long refreshTokenLifetimeDays() {
        return Math.max(1, economyConfig.getRefreshTokenDays());
    }

    private String normalizeIp(String ipAddress) {
        return truncate(ipAddress, 64);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private String describeDevice(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Неизвестное устройство";
        }
        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("iphone")) {
            return "iPhone";
        }
        if (ua.contains("ipad")) {
            return "iPad";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("mac os") || ua.contains("macintosh")) {
            return "macOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }
        return "Неизвестное устройство";
    }
}
