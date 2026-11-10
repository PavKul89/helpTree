package org.example.helptreeservice.service;

import org.example.helptreeservice.config.AuthConfig;
import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.entity.AuthSession;
import org.example.helptreeservice.entity.RefreshToken;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.exception.AccountBlockedException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.repository.AuthSessionRepository;
import org.example.helptreeservice.repository.RefreshTokenRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.AuthSessionService.AuthenticatedUser;
import org.example.helptreeservice.service.AuthSessionService.IssuedTokens;
import org.example.helptreeservice.service.AuthSessionService.SessionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthSessionServiceTest {

    @Mock
    private AuthSessionRepository authSessionRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private TokenService tokenService;

    private AuthConfig authConfig;
    private EconomyConfig economyConfig;
    private AuthSessionService service;

    @BeforeEach
    void setUp() {
        authConfig = new AuthConfig();
        economyConfig = new EconomyConfig();
        service = new AuthSessionService(authSessionRepository, refreshTokenRepository,
                userRepository, jwtService, tokenService, economyConfig, authConfig);

        lenient().when(tokenService.generate()).thenReturn("new-raw-token");
        lenient().when(tokenService.hash(anyString()))
                .thenAnswer(invocation -> "hash:" + invocation.getArgument(0, String.class));
        lenient().when(jwtService.generateToken(anyLong(), anyString(), anyString(), anyString(), anyLong()))
                .thenReturn("access-token");
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@test.com");
        user.setPassword("encoded-password");
        user.setRole(Role.USER);
        user.setTokenVersion(0L);
        user.setEmailVerified(true);
        user.setDeleted(false);
        return user;
    }

    private AuthSession session(User user) {
        LocalDateTime now = LocalDateTime.now();
        return AuthSession.builder()
                .id(UUID.randomUUID())
                .user(user)
                .createdAt(now.minusHours(1))
                .lastUsedAt(now.minusHours(1))
                .expiresAt(now.plusDays(7))
                .build();
    }

    private RefreshToken refreshToken(String raw, AuthSession session, User user) {
        return RefreshToken.builder()
                .id(1L)
                .tokenHash("hash:" + raw)
                .session(session)
                .user(user)
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .expiresAt(session.getExpiresAt())
                .build();
    }

    // ---------------------------------------------------------------
    // Session creation
    // ---------------------------------------------------------------

    @Test
    void createSession_shouldPersistSessionAndHashedRefreshToken() {
        User user = user(1L);
        when(authSessionRepository.save(any(AuthSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IssuedTokens tokens = service.createSession(user, " 203.0.113.5 ", "Mozilla/5.0 Windows");

        assertEquals("access-token", tokens.accessToken());
        assertEquals("new-raw-token", tokens.refreshToken());
        assertEquals(1L, tokens.userId());
        assertEquals(user.getEmail(), tokens.email());
        assertEquals("USER", tokens.role());
        assertTrue(tokens.emailVerified());

        ArgumentCaptor<AuthSession> sessionCaptor = ArgumentCaptor.forClass(AuthSession.class);
        verify(authSessionRepository).save(sessionCaptor.capture());
        AuthSession savedSession = sessionCaptor.getValue();
        assertEquals("203.0.113.5", savedSession.getIpAddress());
        assertTrue(savedSession.getExpiresAt().isAfter(LocalDateTime.now()));
        assertNotNull(savedSession.getLastUsedAt());

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertEquals("hash:new-raw-token", tokenCaptor.getValue().getTokenHash());
        assertEquals(savedSession.getId(), tokenCaptor.getValue().getSession().getId());
        assertEquals(user.getId(), tokenCaptor.getValue().getUser().getId());
    }

    @Test
    void createSession_shouldRejectDeletedUser() {
        User user = user(1L);
        user.setDeleted(true);

        assertThrows(UnauthorizedException.class, () -> service.createSession(user, null, null));
        verify(authSessionRepository, never()).save(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void createSession_shouldAllowBlockedUserForSelfServiceRecovery() {
        User user = user(1L);
        user.setBlockedUntil(LocalDateTime.now().plusDays(1));
        when(authSessionRepository.save(any(AuthSession.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IssuedTokens tokens = service.createSession(user, null, null);

        assertEquals(1L, tokens.userId());
        verify(authSessionRepository).save(any(AuthSession.class));
    }

    // ---------------------------------------------------------------
    // Per-request access token validation
    // ---------------------------------------------------------------

    @Test
    void validateAccessToken_shouldUseCurrentDatabaseRoleAndVersion() {
        User user = user(1L);
        user.setRole(Role.ADMIN);
        user.setTokenVersion(3L);
        AuthSession session = session(user);
        session.setLastUsedAt(LocalDateTime.now().minusMinutes(10));
        when(authSessionRepository.findActiveById(eq(session.getId()), any()))
                .thenReturn(Optional.of(session));

        AuthenticatedUser result = service.validateAccessToken(1L, session.getId().toString(), 3L, "ADMIN");

        assertEquals(1L, result.userId());
        assertEquals("ADMIN", result.role());
        assertEquals(3L, result.tokenVersion());
        assertEquals(session.getId(), result.sessionId());
        assertTrue(session.getLastUsedAt().isAfter(LocalDateTime.now().minusMinutes(1)),
                "lastUsedAt must be refreshed");
    }

    @Test
    void validateAccessToken_shouldTouchLastSeenOnlyWhenStale() {
        User user = user(1L);
        user.setLastSeenAt(LocalDateTime.now().minusMinutes(10));
        AuthSession session = session(user);
        when(authSessionRepository.findActiveById(eq(session.getId()), any()))
                .thenReturn(Optional.of(session));

        service.validateAccessToken(1L, session.getId().toString(), 0L, "USER");

        verify(userRepository).touchLastSeen(eq(1L), any(LocalDateTime.class),
                any(LocalDateTime.class));
    }

    @Test
    void validateAccessToken_shouldSkipRecentLastSeenTouch() {
        User user = user(1L);
        user.setLastSeenAt(LocalDateTime.now());
        AuthSession session = session(user);
        when(authSessionRepository.findActiveById(eq(session.getId()), any()))
                .thenReturn(Optional.of(session));

        service.validateAccessToken(1L, session.getId().toString(), 0L, "USER");

        verify(userRepository, never()).touchLastSeen(anyLong(), any(), any());
    }

    @Test
    void validateAccessToken_shouldRejectDeletedUser() {
        User user = user(1L);
        user.setDeleted(true);
        AuthSession session = session(user);
        when(authSessionRepository.findActiveById(eq(session.getId()), any()))
                .thenReturn(Optional.of(session));

        assertThrows(UnauthorizedException.class,
                () -> service.validateAccessToken(1L, session.getId().toString(), 0L, "USER"));
    }

    @Test
    void validateAccessToken_shouldRejectBlockedUserWith403Signal() {
        User user = user(1L);
        user.setBlockedUntil(LocalDateTime.now().plusHours(1));
        AuthSession session = session(user);
        when(authSessionRepository.findActiveById(eq(session.getId()), any()))
                .thenReturn(Optional.of(session));

        assertThrows(AccountBlockedException.class,
                () -> service.validateAccessToken(1L, session.getId().toString(), 0L, "USER"));
    }

    @Test
    void validateAccessToken_shouldRejectStaleRoleClaim() {
        User user = user(1L);
        AuthSession session = session(user);
        when(authSessionRepository.findActiveById(eq(session.getId()), any()))
                .thenReturn(Optional.of(session));

        assertThrows(UnauthorizedException.class,
                () -> service.validateAccessToken(1L, session.getId().toString(), 0L, "ADMIN"));
    }

    @Test
    void validateAccessToken_shouldRejectStaleTokenVersion() {
        User user = user(1L);
        user.setTokenVersion(2L);
        AuthSession session = session(user);
        when(authSessionRepository.findActiveById(eq(session.getId()), any()))
                .thenReturn(Optional.of(session));

        assertThrows(UnauthorizedException.class,
                () -> service.validateAccessToken(1L, session.getId().toString(), 1L, "USER"));
    }

    @Test
    void validateAccessToken_shouldRejectRevokedSession() {
        UUID sessionId = UUID.randomUUID();
        when(authSessionRepository.findActiveById(eq(sessionId), any())).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class,
                () -> service.validateAccessToken(1L, sessionId.toString(), 0L, "USER"));
    }

    @Test
    void validateAccessToken_shouldRejectMalformedSessionId() {
        assertThrows(UnauthorizedException.class,
                () -> service.validateAccessToken(1L, "not-a-uuid", 0L, "USER"));
    }

    @Test
    void validateAccessToken_shouldRejectMissingClaims() {
        assertThrows(UnauthorizedException.class,
                () -> service.validateAccessToken(null, UUID.randomUUID().toString(), 0L, "USER"));
        assertThrows(UnauthorizedException.class,
                () -> service.validateAccessToken(1L, null, 0L, "USER"));
        assertThrows(UnauthorizedException.class,
                () -> service.validateAccessToken(1L, UUID.randomUUID().toString(), null, "USER"));
        assertThrows(UnauthorizedException.class,
                () -> service.validateAccessToken(1L, UUID.randomUUID().toString(), 0L, null));
    }

    // ---------------------------------------------------------------
    // Refresh-token rotation and replay detection
    // ---------------------------------------------------------------

    @Test
    void rotateRefreshToken_shouldRevokeOldTokenAndIssueNewFamilyToken() {
        User user = user(1L);
        AuthSession session = session(user);
        RefreshToken oldToken = refreshToken("old-raw", session, user);
        when(refreshTokenRepository.findSessionIdByTokenHash("hash:old-raw"))
                .thenReturn(Optional.of(session.getId()));
        when(authSessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
        when(refreshTokenRepository.findByTokenHashForUpdate("hash:old-raw"))
                .thenReturn(Optional.of(oldToken));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IssuedTokens issued = service.rotateRefreshToken("old-raw", "198.51.100.7", "Android");

        assertEquals("new-raw-token", issued.refreshToken());
        assertEquals(session.getId(), issued.sessionId());
        assertNotNull(oldToken.getRevokedAt());
        assertEquals("ROTATED", oldToken.getRevokedReason());
        assertEquals("198.51.100.7", session.getIpAddress());
        assertEquals("Android", session.getUserAgent());
        assertTrue(session.getExpiresAt().isAfter(LocalDateTime.now()));

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(captor.capture());
        assertEquals("hash:new-raw-token", captor.getAllValues().get(1).getTokenHash());
    }

    @Test
    void rotateRefreshToken_shouldRevokeWholeFamilyOnReplay() {
        User user = user(1L);
        AuthSession session = session(user);
        RefreshToken replayed = refreshToken("replayed-raw", session, user);
        replayed.setRevokedAt(LocalDateTime.now().minusMinutes(5));
        replayed.setRevokedReason("ROTATED");
        when(refreshTokenRepository.findSessionIdByTokenHash("hash:replayed-raw"))
                .thenReturn(Optional.of(session.getId()));
        when(authSessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
        when(refreshTokenRepository.findByTokenHashForUpdate("hash:replayed-raw"))
                .thenReturn(Optional.of(replayed));

        assertThrows(UnauthorizedException.class,
                () -> service.rotateRefreshToken("replayed-raw", null, null));

        assertNotNull(session.getRevokedAt());
        assertEquals("REFRESH_REPLAY", session.getRevokedReason());
        verify(authSessionRepository).save(session);
        verify(refreshTokenRepository).revokeAllBySessionId(
                eq(session.getId()), any(LocalDateTime.class), eq("REFRESH_REPLAY"));
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void rotateRefreshToken_shouldRejectExpiredToken() {
        User user = user(1L);
        AuthSession session = session(user);
        RefreshToken expired = refreshToken("expired-raw", session, user);
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(refreshTokenRepository.findSessionIdByTokenHash("hash:expired-raw"))
                .thenReturn(Optional.of(session.getId()));
        when(authSessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
        when(refreshTokenRepository.findByTokenHashForUpdate("hash:expired-raw"))
                .thenReturn(Optional.of(expired));

        assertThrows(UnauthorizedException.class,
                () -> service.rotateRefreshToken("expired-raw", null, null));

        assertEquals("EXPIRED", expired.getRevokedReason());
        assertNotNull(expired.getRevokedAt());
    }

    @Test
    void rotateRefreshToken_shouldRejectInactiveSession() {
        User user = user(1L);
        AuthSession session = session(user);
        session.setRevokedAt(LocalDateTime.now().minusMinutes(1));
        RefreshToken token = refreshToken("raw", session, user);
        when(refreshTokenRepository.findSessionIdByTokenHash("hash:raw"))
                .thenReturn(Optional.of(session.getId()));
        when(authSessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
        when(refreshTokenRepository.findByTokenHashForUpdate("hash:raw")).thenReturn(Optional.of(token));

        assertThrows(UnauthorizedException.class,
                () -> service.rotateRefreshToken("raw", null, null));

        assertEquals("SESSION_INACTIVE", token.getRevokedReason());
    }

    @Test
    void rotateRefreshToken_shouldAllowBlockedUserToRecoverSession() {
        User user = user(1L);
        user.setBlockedUntil(LocalDateTime.now().plusDays(1));
        AuthSession session = session(user);
        RefreshToken token = refreshToken("raw", session, user);
        when(refreshTokenRepository.findSessionIdByTokenHash("hash:raw"))
                .thenReturn(Optional.of(session.getId()));
        when(authSessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
        when(refreshTokenRepository.findByTokenHashForUpdate("hash:raw")).thenReturn(Optional.of(token));
        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        IssuedTokens tokens = service.rotateRefreshToken("raw", null, null);

        assertEquals("access-token", tokens.accessToken());
        assertNotNull(token.getRevokedAt());
    }

    @Test
    void rotateRefreshToken_shouldRejectTokenFromAnotherSession() {
        User user = user(1L);
        AuthSession session = session(user);
        User otherUser = user(2L);
        AuthSession otherSession = session(otherUser);
        RefreshToken foreignToken = refreshToken("raw", otherSession, otherUser);
        when(refreshTokenRepository.findSessionIdByTokenHash("hash:raw"))
                .thenReturn(Optional.of(session.getId()));
        when(authSessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
        when(refreshTokenRepository.findByTokenHashForUpdate("hash:raw"))
                .thenReturn(Optional.of(foreignToken));

        assertThrows(UnauthorizedException.class,
                () -> service.rotateRefreshToken("raw", null, null));
    }

    @Test
    void rotateRefreshToken_shouldRejectUnknownToken() {
        when(refreshTokenRepository.findSessionIdByTokenHash("hash:unknown"))
                .thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class,
                () -> service.rotateRefreshToken("unknown", null, null));
        verify(authSessionRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void rotateRefreshToken_shouldRejectBlankToken() {
        assertThrows(UnauthorizedException.class,
                () -> service.rotateRefreshToken("  ", null, null));
    }

    // ---------------------------------------------------------------
    // Session management / IDOR protection
    // ---------------------------------------------------------------

    @Test
    void revokeSession_shouldRejectForeignSession() {
        UUID sessionId = UUID.randomUUID();
        when(authSessionRepository.findByIdAndUserIdForUpdate(sessionId, 2L))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.revokeSession(2L, sessionId, "USER_REVOKED"));

        verify(authSessionRepository, never()).save(any());
        verify(refreshTokenRepository, never())
                .revokeAllBySessionId(any(), any(), anyString());
    }

    @Test
    void revokeSession_shouldRevokeSessionAndItsTokens() {
        User user = user(1L);
        AuthSession session = session(user);
        when(authSessionRepository.findByIdAndUserIdForUpdate(session.getId(), 1L))
                .thenReturn(Optional.of(session));

        service.revokeSession(1L, session.getId(), "USER_REVOKED");

        assertNotNull(session.getRevokedAt());
        assertEquals("USER_REVOKED", session.getRevokedReason());
        verify(authSessionRepository).save(session);
        verify(refreshTokenRepository).revokeAllBySessionId(
                eq(session.getId()), any(LocalDateTime.class), eq("USER_REVOKED"));
    }

    @Test
    void revokeAllSessionsForUser_shouldIncrementTokenVersionAndRevokeEverything() {
        User user = user(1L);
        user.setTokenVersion(4L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        service.revokeAllSessionsForUser(1L, "LOGOUT_ALL");

        assertEquals(5L, user.getTokenVersion());
        verify(userRepository).save(user);
        verify(authSessionRepository).revokeAllActiveByUserId(
                eq(1L), any(LocalDateTime.class), eq("LOGOUT_ALL"));
        verify(refreshTokenRepository).revokeAllByUserId(
                eq(1L), any(LocalDateTime.class), eq("LOGOUT_ALL"));
    }

    @Test
    void revokeAllSessionsForUser_shouldRejectUnknownUser() {
        when(userRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.revokeAllSessionsForUser(99L, "LOGOUT_ALL"));
    }

    @Test
    void listActiveSessions_shouldExposeDeviceMetadataAndCurrentFlag() {
        User user = user(1L);
        AuthSession current = session(user);
        AuthSession other = session(user);
        other.setUserAgent("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)");
        other.setLastUsedAt(null);
        when(authSessionRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(eq(1L), any()))
                .thenReturn(List.of(current, other));

        List<SessionInfo> sessions = service.listActiveSessions(1L, current.getId());

        assertEquals(2, sessions.size());
        assertTrue(sessions.get(0).current());
        assertFalse(sessions.get(1).current());
        assertEquals("iPhone", sessions.get(1).deviceName());
        assertEquals(other.getCreatedAt(), sessions.get(1).lastUsedAt());
        assertNotNull(sessions.get(0).createdAt());
        assertNotNull(sessions.get(0).expiresAt());
    }

    @Test
    void revokeSessionByRefreshToken_shouldEndOwningSession() {
        User user = user(1L);
        AuthSession session = session(user);
        RefreshToken token = refreshToken("raw", session, user);
        when(refreshTokenRepository.findSessionIdByTokenHash("hash:raw"))
                .thenReturn(Optional.of(session.getId()));
        when(authSessionRepository.findByIdForUpdate(session.getId())).thenReturn(Optional.of(session));
        when(refreshTokenRepository.findByTokenHashForUpdate("hash:raw")).thenReturn(Optional.of(token));

        UUID revoked = service.revokeSessionByRefreshToken("raw", "LOGOUT");

        assertEquals(session.getId(), revoked);
        assertNotNull(session.getRevokedAt());
    }

    @Test
    void findSessionIdByRawToken_shouldReturnEmptyForMissingToken() {
        when(refreshTokenRepository.findByTokenHash("hash:missing")).thenReturn(Optional.empty());
        assertTrue(service.findSessionIdByRawToken("missing").isEmpty());
        assertTrue(service.findSessionIdByRawToken(null).isEmpty());
    }

    @Test
    void cleanupExpiredSessions_shouldDeleteExpiredRows() {
        when(authSessionRepository.deleteExpiredOrOldRevoked(any(), any())).thenReturn(3);
        when(refreshTokenRepository.deleteExpiredBefore(any())).thenReturn(2);

        service.cleanupExpiredSessions();

        verify(authSessionRepository).deleteExpiredOrOldRevoked(any(LocalDateTime.class),
                any(LocalDateTime.class));
        verify(refreshTokenRepository).deleteExpiredBefore(any(LocalDateTime.class));
    }
}
