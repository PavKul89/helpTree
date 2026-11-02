package org.example.helptreeservice.service;

import org.example.helptreeservice.config.AuthConfig;
import org.example.helptreeservice.entity.AccountToken;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.AccountTokenType;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.AccountTokenRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountSecurityServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountTokenRepository accountTokenRepository;
    @Mock
    private PasswordService passwordService;
    @Mock
    private AuthSessionService authSessionService;
    @Mock
    private TokenService tokenService;
    @Mock
    private EmailService emailService;

    private AuthConfig authConfig;
    private AccountSecurityService service;

    @BeforeEach
    void setUp() {
        authConfig = new AuthConfig();
        service = new AccountSecurityService(userRepository, accountTokenRepository,
                passwordService, authSessionService, tokenService, emailService, authConfig);

        lenient().when(tokenService.generate()).thenReturn("raw-token");
        lenient().when(tokenService.hash(anyString()))
                .thenAnswer(invocation -> "hash:" + invocation.getArgument(0, String.class));
        lenient().when(emailService.isAvailable()).thenReturn(true);
        lenient().when(accountTokenRepository.save(any(AccountToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(passwordService.encode(anyString())).thenReturn("new-encoded-password");
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@test.com");
        user.setPassword("stored-password");
        user.setRole(Role.USER);
        user.setTokenVersion(0L);
        user.setEmailVerified(false);
        user.setDeleted(false);
        return user;
    }

    private AccountToken token(User user, AccountTokenType type, String payload) {
        LocalDateTime now = LocalDateTime.now();
        return AccountToken.builder()
                .id(10L)
                .user(user)
                .type(type)
                .tokenHash("hash:raw-token")
                .payload(payload)
                .createdAt(now.minusMinutes(1))
                .expiresAt(now.plusHours(1))
                .build();
    }

    private void stubConsumableToken(User user, AccountTokenType type, AccountToken accountToken) {
        when(accountTokenRepository.findUserIdByTokenHash("hash:raw-token"))
                .thenReturn(Optional.of(user.getId()));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(accountTokenRepository.consume(eq("hash:raw-token"), eq(type), any(LocalDateTime.class)))
                .thenReturn(1);
        when(accountTokenRepository.findByTokenHash("hash:raw-token"))
                .thenReturn(Optional.of(accountToken));
    }

    // ---------------------------------------------------------------
    // Email verification
    // ---------------------------------------------------------------

    @Test
    void confirmEmailVerification_shouldMarkAddressVerified() {
        User user = user(1L);
        AccountToken accountToken = token(user, AccountTokenType.EMAIL_VERIFICATION, null);
        stubConsumableToken(user, AccountTokenType.EMAIL_VERIFICATION, accountToken);

        service.confirmEmailVerification("raw-token");

        assertTrue(user.getEmailVerified());
        verify(userRepository).save(user);
    }

    @Test
    void confirmEmailVerification_shouldRejectReplayedToken() {
        User user = user(1L);
        when(accountTokenRepository.findUserIdByTokenHash("hash:raw-token"))
                .thenReturn(Optional.of(1L));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(accountTokenRepository.consume(eq("hash:raw-token"),
                eq(AccountTokenType.EMAIL_VERIFICATION), any(LocalDateTime.class))).thenReturn(0);

        assertThrows(BadRequestException.class,
                () -> service.confirmEmailVerification("raw-token"));

        assertFalse(user.getEmailVerified());
        verify(userRepository, never()).save(any());
    }

    @Test
    void confirmEmailVerification_shouldRejectUnknownToken() {
        when(accountTokenRepository.findUserIdByTokenHash("hash:unknown"))
                .thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> service.confirmEmailVerification("unknown"));

        verify(userRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void confirmEmailVerification_shouldRejectDeletedUser() {
        User user = user(1L);
        user.setDeleted(true);
        when(accountTokenRepository.findUserIdByTokenHash("hash:raw-token"))
                .thenReturn(Optional.of(1L));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThrows(BadRequestException.class,
                () -> service.confirmEmailVerification("raw-token"));

        verify(accountTokenRepository, never())
                .consume(anyString(), any(), any(LocalDateTime.class));
    }

    @Test
    void requestEmailVerification_shouldSkipVerifiedAddress() {
        User user = user(1L);
        user.setEmailVerified(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        service.requestEmailVerification(1L);

        verify(emailService, never()).sendEmailVerification(anyString(), anyString());
        verify(accountTokenRepository, never()).save(any());
    }

    @Test
    void requestEmailVerification_shouldRejectWhenMailIsDisabled() {
        User user = user(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(emailService.isAvailable()).thenReturn(false);

        assertThrows(BadRequestException.class, () -> service.requestEmailVerification(1L));
    }

    // ---------------------------------------------------------------
    // Password reset
    // ---------------------------------------------------------------

    @Test
    void requestPasswordReset_shouldNotRevealUnknownAccounts() {
        when(userRepository.findByEmailIgnoreCase("missing@test.com")).thenReturn(Optional.empty());

        service.requestPasswordReset("missing@test.com");

        verify(accountTokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordReset(anyString(), anyString());
    }

    @Test
    void requestPasswordReset_shouldCreateHashedTokenForKnownAccount() {
        User user = user(1L);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(emailService.sendPasswordReset(user.getEmail(), "raw-token")).thenReturn(true);

        service.requestPasswordReset(user.getEmail());

        ArgumentCaptor<AccountToken> captor = ArgumentCaptor.forClass(AccountToken.class);
        verify(accountTokenRepository).save(captor.capture());
        AccountToken saved = captor.getValue();
        assertEquals(AccountTokenType.PASSWORD_RESET, saved.getType());
        assertEquals("hash:raw-token", saved.getTokenHash());
        assertNull(saved.getPayload());
        verify(emailService).sendPasswordReset(user.getEmail(), "raw-token");
        verify(accountTokenRepository).revokeActiveByUserAndType(
                eq(1L), eq(AccountTokenType.PASSWORD_RESET), any(LocalDateTime.class));
    }

    @Test
    void requestPasswordReset_shouldNotTouchDatabaseWhenMailIsDisabled() {
        when(emailService.isAvailable()).thenReturn(false);

        service.requestPasswordReset("user@test.com");

        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
        verify(accountTokenRepository, never()).save(any());
    }

    @Test
    void confirmPasswordReset_shouldUpdatePasswordVerifyEmailAndRevokeSessions() {
        User user = user(1L);
        AccountToken accountToken = token(user, AccountTokenType.PASSWORD_RESET, null);
        stubConsumableToken(user, AccountTokenType.PASSWORD_RESET, accountToken);

        service.confirmPasswordReset("raw-token", "NewStrong1");

        assertEquals("new-encoded-password", user.getPassword());
        assertTrue(user.getEmailVerified());
        verify(passwordService).validate("NewStrong1");
        verify(userRepository).save(user);
        verify(authSessionService).revokeAllSessionsForUser(1L, "PASSWORD_RESET");
    }

    @Test
    void confirmPasswordReset_shouldRejectReplayedTokenWithoutChangingPassword() {
        User user = user(1L);
        String originalPassword = user.getPassword();
        when(accountTokenRepository.findUserIdByTokenHash("hash:raw-token"))
                .thenReturn(Optional.of(1L));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(accountTokenRepository.consume(eq("hash:raw-token"),
                eq(AccountTokenType.PASSWORD_RESET), any(LocalDateTime.class))).thenReturn(0);

        assertThrows(BadRequestException.class,
                () -> service.confirmPasswordReset("raw-token", "NewStrong1"));

        assertEquals(originalPassword, user.getPassword());
        verify(authSessionService, never()).revokeAllSessionsForUser(anyLong(), anyString());
        verify(userRepository, never()).save(any());
    }

    // ---------------------------------------------------------------
    // Password change
    // ---------------------------------------------------------------

    @Test
    void changePassword_shouldRequireCorrectCurrentPassword() {
        User user = user(1L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwordService.matches("wrong", "stored-password")).thenReturn(false);

        assertThrows(BadRequestException.class,
                () -> service.changePassword(1L, "wrong", "NewStrong1"));

        verify(passwordService, never()).encode(anyString());
        verify(userRepository, never()).save(any());
        verify(authSessionService, never()).revokeAllSessionsForUser(anyLong(), anyString());
    }

    @Test
    void changePassword_shouldRejectReusingCurrentPassword() {
        User user = user(1L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwordService.matches("Current1", "stored-password")).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> service.changePassword(1L, "Current1", "Current1"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void changePassword_shouldUpdatePasswordAndRevokeAllSessions() {
        User user = user(1L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwordService.matches("Current1", "stored-password")).thenReturn(true);
        when(passwordService.matches("NewStrong1", "stored-password")).thenReturn(false);

        service.changePassword(1L, "Current1", "NewStrong1");

        assertEquals("new-encoded-password", user.getPassword());
        verify(passwordService).validate("NewStrong1");
        verify(userRepository).save(user);
        verify(authSessionService).revokeAllSessionsForUser(1L, "PASSWORD_CHANGED");
    }

    @Test
    void changePassword_shouldRejectDeletedUser() {
        User user = user(1L);
        user.setDeleted(true);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        assertThrows(NotFoundException.class,
                () -> service.changePassword(1L, "Current1", "NewStrong1"));
    }

    // ---------------------------------------------------------------
    // Email change
    // ---------------------------------------------------------------

    @Test
    void requestEmailChange_shouldNormalizeAndSendConfirmationToNewAddress() {
        User user = user(1L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwordService.matches("Current1", "stored-password")).thenReturn(true);
        when(userRepository.existsByEmailIgnoreCase("new@test.com")).thenReturn(false);
        when(emailService.sendEmailChangeConfirmation("new@test.com", "new@test.com", "raw-token"))
                .thenReturn(true);

        service.requestEmailChange(1L, " New@Test.COM ", "Current1");

        ArgumentCaptor<AccountToken> captor = ArgumentCaptor.forClass(AccountToken.class);
        verify(accountTokenRepository).save(captor.capture());
        assertEquals(AccountTokenType.EMAIL_CHANGE, captor.getValue().getType());
        assertEquals("new@test.com", captor.getValue().getPayload());
        verify(emailService).sendEmailChangeConfirmation("new@test.com", "new@test.com", "raw-token");
    }

    @Test
    void requestEmailChange_shouldRequireCorrectPassword() {
        User user = user(1L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwordService.matches("wrong", "stored-password")).thenReturn(false);

        assertThrows(BadRequestException.class,
                () -> service.requestEmailChange(1L, "new@test.com", "wrong"));

        verify(userRepository, never()).existsByEmailIgnoreCase(anyString());
        verify(accountTokenRepository, never()).save(any());
    }

    @Test
    void requestEmailChange_shouldRejectSameAddress() {
        User user = user(1L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwordService.matches("Current1", "stored-password")).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> service.requestEmailChange(1L, "USER1@test.com", "Current1"));

        verify(accountTokenRepository, never()).save(any());
    }

    @Test
    void requestEmailChange_shouldRejectAddressAlreadyInUse() {
        User user = user(1L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwordService.matches("Current1", "stored-password")).thenReturn(true);
        when(userRepository.existsByEmailIgnoreCase("taken@test.com")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.requestEmailChange(1L, "taken@test.com", "Current1"));

        verify(accountTokenRepository, never()).save(any());
    }

    @Test
    void requestEmailChange_shouldRejectWhenMailIsDisabled() {
        User user = user(1L);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(passwordService.matches("Current1", "stored-password")).thenReturn(true);
        when(userRepository.existsByEmailIgnoreCase("new@test.com")).thenReturn(false);
        when(emailService.isAvailable()).thenReturn(false);

        assertThrows(BadRequestException.class,
                () -> service.requestEmailChange(1L, "new@test.com", "Current1"));

        verify(accountTokenRepository, never()).save(any());
    }

    @Test
    void confirmEmailChange_shouldApplyAddressAndRevokeSessions() {
        User user = user(1L);
        String oldEmail = user.getEmail();
        AccountToken accountToken = token(user, AccountTokenType.EMAIL_CHANGE, "new@test.com");
        stubConsumableToken(user, AccountTokenType.EMAIL_CHANGE, accountToken);
        when(userRepository.existsByEmailIgnoreCase("new@test.com")).thenReturn(false);

        service.confirmEmailChange("raw-token");

        assertEquals("new@test.com", user.getEmail());
        assertTrue(user.getEmailVerified());
        verify(userRepository).save(user);
        verify(authSessionService).revokeAllSessionsForUser(1L, "EMAIL_CHANGED");
        verify(emailService).sendEmailChangedNotice(oldEmail, "new@test.com");
    }

    @Test
    void confirmEmailChange_shouldRejectAddressTakenSinceRequest() {
        User user = user(1L);
        String oldEmail = user.getEmail();
        AccountToken accountToken = token(user, AccountTokenType.EMAIL_CHANGE, "taken@test.com");
        stubConsumableToken(user, AccountTokenType.EMAIL_CHANGE, accountToken);
        when(userRepository.existsByEmailIgnoreCase("taken@test.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.confirmEmailChange("raw-token"));

        assertEquals(oldEmail, user.getEmail());
        verify(authSessionService, never()).revokeAllSessionsForUser(anyLong(), anyString());
    }

    @Test
    void confirmEmailChange_shouldRejectTokenWithoutPayload() {
        User user = user(1L);
        AccountToken accountToken = token(user, AccountTokenType.EMAIL_CHANGE, null);
        stubConsumableToken(user, AccountTokenType.EMAIL_CHANGE, accountToken);

        assertThrows(BadRequestException.class,
                () -> service.confirmEmailChange("raw-token"));

        verify(authSessionService, never()).revokeAllSessionsForUser(anyLong(), anyString());
    }

    // ---------------------------------------------------------------
    // Registration verification email
    // ---------------------------------------------------------------

    @Test
    void sendRegistrationVerification_shouldSwallowMailFailures() {
        User user = user(1L);
        when(emailService.sendEmailVerification(user.getEmail(), "raw-token"))
                .thenThrow(new IllegalStateException("SMTP down"));

        service.sendRegistrationVerification(user);

        verify(accountTokenRepository).save(any(AccountToken.class));
    }

    @Test
    void sendRegistrationVerification_shouldSkipUnverifiedMailDisabled() {
        User user = user(1L);
        when(emailService.isAvailable()).thenReturn(false);

        service.sendRegistrationVerification(user);

        verify(accountTokenRepository, never()).save(any());
    }

    @Test
    void cleanupExpiredTokens_shouldDeleteExpiredRows() {
        when(accountTokenRepository.deleteExpiredBefore(any(LocalDateTime.class))).thenReturn(4);

        service.cleanupExpiredTokens();

        verify(accountTokenRepository).deleteExpiredBefore(any(LocalDateTime.class));
    }

    @Test
    void createAccountToken_shouldStoreOnlyHashAndExpiry() {
        User user = user(1L);
        when(emailService.sendPasswordReset(user.getEmail(), "raw-token")).thenReturn(true);
        when(userRepository.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));

        service.requestPasswordReset(user.getEmail());

        ArgumentCaptor<AccountToken> captor = ArgumentCaptor.forClass(AccountToken.class);
        verify(accountTokenRepository).save(captor.capture());
        AccountToken saved = captor.getValue();
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getExpiresAt());
        assertTrue(saved.getExpiresAt().isAfter(LocalDateTime.now()));
        assertNotEquals("raw-token", saved.getTokenHash());
        assertEquals("hash:raw-token", saved.getTokenHash());
    }
}
