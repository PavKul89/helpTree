package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.auth.AuthResponse;
import org.example.helptreeservice.dto.auth.LoginRequest;
import org.example.helptreeservice.dto.auth.SessionDto;
import org.example.helptreeservice.dto.users.CreateUserRequest;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.RateLimitExceededException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.AuthSessionService.IssuedTokens;
import org.example.helptreeservice.service.ratelimit.LoginProtectionService;
import org.example.helptreeservice.metrics.HelpTreeMetrics;
import org.example.helptreeservice.service.AuthSessionService.SessionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserService userService;
    @Mock
    private PasswordService passwordService;
    @Mock
    private JwtService jwtService;
    @Mock
    private AuthSessionService authSessionService;
    @Mock
    private AccountSecurityService accountSecurityService;
    @Mock
    private LoginProtectionService loginProtectionService;
    @Mock
    private HelpTreeMetrics metrics;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, userService, passwordService,
                jwtService, authSessionService, accountSecurityService, loginProtectionService,
                metrics);
        lenient().when(jwtService.getExpirationTime()).thenReturn(86_400_000L);
        lenient().when(loginProtectionService.retryAfterSeconds(any(), any()))
                .thenReturn(Optional.empty());
        lenient().when(loginProtectionService.recordFailure(any(), any()))
                .thenReturn(Optional.empty());
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@test.com");
        user.setPassword("stored-password");
        user.setRole(Role.USER);
        user.setTokenVersion(0L);
        user.setEmailVerified(true);
        user.setDeleted(false);
        return user;
    }

    private IssuedTokens tokens(User user) {
        return new IssuedTokens("access-token", "refresh-token", UUID.randomUUID(),
                user.getId(), user.getEmail(), user.getRole().name(),
                Boolean.TRUE.equals(user.getEmailVerified()));
    }

    // ---------------------------------------------------------------
    // Registration
    // ---------------------------------------------------------------

    @Test
    void register_shouldCreateSessionAndSendVerificationAfterCommit() {
        CreateUserRequest request = new CreateUserRequest();
        request.setEmail("new@test.com");
        User user = user(10L);
        UserDto dto = UserDto.builder().id(10L).email("new@test.com").build();
        IssuedTokens issued = tokens(user);

        when(userService.createUser(request)).thenReturn(dto);
        when(userService.getUserEntityById(10L)).thenReturn(user);
        when(authSessionService.createSession(user, null, null)).thenReturn(issued);

        AuthResponse response = service.register(request);

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(10L, response.getUserId());
        assertEquals(issued.sessionId().toString(), response.getSessionId());
        assertTrue(response.getEmailVerified());
        assertEquals(86_400_000L, response.getExpiresIn());
        verify(accountSecurityService).sendRegistrationVerification(user);
    }

    // ---------------------------------------------------------------
    // Login
    // ---------------------------------------------------------------

    @Test
    void login_shouldNormalizeEmailAndReturnPreviousLoginTime() {
        LoginRequest request = new LoginRequest();
        request.setEmail("  USER1@Test.COM ");
        request.setPassword("Current1");

        User user = user(1L);
        LocalDateTime previousLogin = LocalDateTime.now().minusDays(2);
        user.setLastLogin(previousLogin);
        IssuedTokens issued = tokens(user);
        when(userRepository.findByEmailIgnoreCase("user1@test.com")).thenReturn(Optional.of(user));
        when(passwordService.matches("Current1", "stored-password")).thenReturn(true);
        when(authSessionService.createSession(user, "203.0.113.9", "JUnit"))
                .thenReturn(issued);

        AuthResponse response = service.login(request, "203.0.113.9", "JUnit");

        assertEquals("access-token", response.getAccessToken());
        assertEquals(previousLogin, response.getPreviousLastLogin());
        verify(userService).updateLastLogin(1L);
        verify(authSessionService).createSession(user, "203.0.113.9", "JUnit");
        verify(metrics).loginSuccess();
    }

    @Test
    void login_shouldUseGenericErrorForUnknownAccount() {
        LoginRequest request = new LoginRequest();
        request.setEmail("missing@test.com");
        request.setPassword("Current1");
        when(userRepository.findByEmailIgnoreCase("missing@test.com")).thenReturn(Optional.empty());

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> service.login(request, null, null));

        assertEquals("Неверный email или пароль", ex.getMessage());
        verify(passwordService).matches(eq("Current1"), anyString());
        verify(authSessionService, never()).createSession(any(), any(), any());
        verify(metrics).loginFailure("unknown_account");
    }

    @Test
    void login_shouldRejectWrongPassword() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user1@test.com");
        request.setPassword("Wrong1");
        User user = user(1L);
        when(userRepository.findByEmailIgnoreCase("user1@test.com")).thenReturn(Optional.of(user));
        when(passwordService.matches("Wrong1", "stored-password")).thenReturn(false);

        assertThrows(UnauthorizedException.class,
                () -> service.login(request, null, null));

        verify(userService, never()).updateLastLogin(any());
    }

    @Test
    void login_shouldRejectDeletedAccount() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user1@test.com");
        request.setPassword("Current1");
        User user = user(1L);
        user.setDeleted(true);
        when(userRepository.findByEmailIgnoreCase("user1@test.com")).thenReturn(Optional.of(user));
        when(passwordService.matches("Current1", "stored-password")).thenReturn(true);

        assertThrows(UnauthorizedException.class,
                () -> service.login(request, null, null));

        verify(authSessionService, never()).createSession(any(), any(), any());
    }

    @Test
    void login_shouldAllowBlockedAccountSoItCanBuyUnblock() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user1@test.com");
        request.setPassword("Current1");
        User user = user(1L);
        user.setBlockedUntil(LocalDateTime.now().plusDays(1));
        IssuedTokens issued = tokens(user);
        when(userRepository.findByEmailIgnoreCase("user1@test.com")).thenReturn(Optional.of(user));
        when(passwordService.matches("Current1", "stored-password")).thenReturn(true);
        when(authSessionService.createSession(user, null, null)).thenReturn(issued);

        AuthResponse response = service.login(request, null, null);

        assertEquals("access-token", response.getAccessToken());
        verify(authSessionService).createSession(user, null, null);
    }

    @Test
    void login_shouldLockAccountTemporarilyAfterRepeatedFailures() {
        LoginRequest request = new LoginRequest();
        request.setEmail("bruteforce@test.com");
        request.setPassword("Wrong1");
        when(userRepository.findByEmailIgnoreCase("bruteforce@test.com")).thenReturn(Optional.empty());
        when(loginProtectionService.recordFailure(null, "bruteforce@test.com"))
                .thenReturn(Optional.empty(), Optional.empty(), Optional.empty(),
                        Optional.empty(), Optional.of(900L));

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThrows(UnauthorizedException.class,
                    () -> service.login(request, null, null));
        }

        // The fifth failure both records the attempt and returns the lockout.
        RateLimitExceededException error = assertThrows(RateLimitExceededException.class,
                () -> service.login(request, null, null));
        assertEquals(900L, error.getRetryAfterSeconds());
    }

    @Test
    void login_shouldRejectLockedPairBeforeTouchingTheDatabase() {
        LoginRequest request = new LoginRequest();
        request.setEmail("locked@test.com");
        request.setPassword("Wrong1");
        when(loginProtectionService.retryAfterSeconds(null, "locked@test.com"))
                .thenReturn(Optional.of(321L));

        RateLimitExceededException error = assertThrows(RateLimitExceededException.class,
                () -> service.login(request, null, null));

        assertEquals(321L, error.getRetryAfterSeconds());
        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void login_shouldClearFailuresAfterSuccessfulAuthentication() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user1@test.com");
        request.setPassword("Current1");
        User user = user(1L);
        when(userRepository.findByEmailIgnoreCase("user1@test.com")).thenReturn(Optional.of(user));
        when(passwordService.matches("Current1", "stored-password")).thenReturn(true);
        when(authSessionService.createSession(user, "203.0.113.9", "JUnit"))
                .thenReturn(tokens(user));

        service.login(request, "203.0.113.9", "JUnit");

        verify(loginProtectionService).recordSuccess("203.0.113.9", "user1@test.com");
    }

    // ---------------------------------------------------------------
    // Refresh
    // ---------------------------------------------------------------

    @Test
    void refresh_shouldMapRotatedTokens() {
        User user = user(1L);
        IssuedTokens issued = tokens(user);
        when(authSessionService.rotateRefreshToken("old-refresh", "203.0.113.1", "JUnit"))
                .thenReturn(issued);

        AuthResponse response = service.refresh("old-refresh", "203.0.113.1", "JUnit");

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals(issued.sessionId().toString(), response.getSessionId());
        assertNull(response.getPreviousLastLogin());
    }

    // ---------------------------------------------------------------
    // Logout / session management
    // ---------------------------------------------------------------

    @Test
    void logout_shouldRevokeOnlyCurrentSession() {
        UUID sessionId = UUID.randomUUID();

        service.logout(1L, sessionId, null);

        verify(authSessionService).revokeSession(1L, sessionId, "LOGOUT");
    }

    @Test
    void logout_shouldAcceptRefreshTokenBelongingToCurrentSession() {
        UUID sessionId = UUID.randomUUID();
        when(authSessionService.findSessionIdByRawToken("refresh"))
                .thenReturn(Optional.of(sessionId));

        service.logout(1L, sessionId, "refresh");

        verify(authSessionService).revokeSession(1L, sessionId, "LOGOUT");
    }

    @Test
    void logout_shouldRejectRefreshTokenFromAnotherSession() {
        UUID currentSession = UUID.randomUUID();
        UUID foreignSession = UUID.randomUUID();
        when(authSessionService.findSessionIdByRawToken("foreign"))
                .thenReturn(Optional.of(foreignSession));

        assertThrows(ForbiddenException.class,
                () -> service.logout(1L, currentSession, "foreign"));

        verify(authSessionService, never()).revokeSession(any(), any(), anyString());
    }

    @Test
    void logout_shouldRejectUnknownRefreshToken() {
        UUID currentSession = UUID.randomUUID();
        when(authSessionService.findSessionIdByRawToken("unknown")).thenReturn(Optional.empty());

        assertThrows(ForbiddenException.class,
                () -> service.logout(1L, currentSession, "unknown"));

        verify(authSessionService, never()).revokeSession(any(), any(), anyString());
    }

    @Test
    void logout_shouldRequireAuthenticatedUserAndSession() {
        UUID sessionId = UUID.randomUUID();

        assertThrows(UnauthorizedException.class, () -> service.logout(null, sessionId, null));
        assertThrows(UnauthorizedException.class, () -> service.logout(1L, null, null));
        verify(authSessionService, never()).revokeSession(any(), any(), anyString());
    }

    @Test
    void logoutAll_shouldRevokeEverySession() {
        service.logoutAll(1L);
        verify(authSessionService).revokeAllSessionsForUser(1L, "LOGOUT_ALL");
    }

    @Test
    void listSessions_shouldMapSessionMetadata() {
        User user = user(1L);
        UUID currentId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        when(authSessionService.listActiveSessions(1L, currentId)).thenReturn(List.of(
                new SessionInfo(currentId, now.minusDays(1), now, now.plusDays(6),
                        "203.0.113.1", "JUnit", "Windows", true),
                new SessionInfo(otherId, now.minusDays(2), now.minusHours(1), now.plusDays(5),
                        "203.0.113.2", "iPhone", "iPhone", false)
        ));

        List<SessionDto> sessions = service.listSessions(1L, currentId);

        assertEquals(2, sessions.size());
        assertEquals(currentId.toString(), sessions.get(0).getId());
        assertTrue(sessions.get(0).isCurrent());
        assertEquals("Windows", sessions.get(0).getDeviceName());
        assertEquals(otherId.toString(), sessions.get(1).getId());
        assertEquals("203.0.113.2", sessions.get(1).getIpAddress());
    }

    @Test
    void revokeSession_shouldRequireSessionId() {
        assertThrows(BadRequestException.class, () -> service.revokeSession(1L, null));
        verify(authSessionService, never()).revokeSession(any(), any(), anyString());
    }

    @Test
    void revokeSession_shouldDelegateWithCurrentUser() {
        UUID sessionId = UUID.randomUUID();

        service.revokeSession(1L, sessionId);

        verify(authSessionService).revokeSession(1L, sessionId, "USER_REVOKED");
    }

    // ---------------------------------------------------------------
    // Delegation to account security
    // ---------------------------------------------------------------

    @Test
    void changePassword_shouldDelegate() {
        service.changePassword(1L, "Current1", "NewStrong1");
        verify(accountSecurityService).changePassword(1L, "Current1", "NewStrong1");
    }

    @Test
    void requestPasswordReset_shouldNormalizeEmail() {
        service.requestPasswordReset("  USER@Test.COM ");
        verify(accountSecurityService).requestPasswordReset("user@test.com");
    }

    @Test
    void getCurrentUser_shouldDelegate() {
        UserDto dto = UserDto.builder().id(1L).build();
        when(userService.getUserById(1L)).thenReturn(dto);

        assertEquals(dto, service.getCurrentUser(1L));
    }
}
