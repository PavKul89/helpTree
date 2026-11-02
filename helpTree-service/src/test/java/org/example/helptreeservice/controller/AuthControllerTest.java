package org.example.helptreeservice.controller;

import org.example.helptreeservice.dto.auth.LogoutRequest;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.security.ClientInfoResolver;
import org.example.helptreeservice.service.AuthService;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.AuthorizationService.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private AuthorizationService authorizationService;
    @Mock
    private ClientInfoResolver clientInfoResolver;

    @InjectMocks
    private AuthController controller;

    @BeforeEach
    void allowNoUserByDefault() {
        lenient().when(authorizationService.getCurrentUser()).thenReturn(null);
        lenient().when(authorizationService.getCurrentSessionId()).thenReturn(null);
    }

    @Test
    void logout_shouldRequireAuthenticatedUser() {
        assertThrows(UnauthorizedException.class,
                () -> controller.logout(new LogoutRequest()));

        verify(authService, never()).logout(any(), any(), any());
    }

    @Test
    void logout_shouldRequireCurrentSession() {
        when(authorizationService.getCurrentUser())
                .thenReturn(new UserContext(1L, "USER", "user@test.com"));

        assertThrows(UnauthorizedException.class,
                () -> controller.logout(new LogoutRequest()));

        verify(authService, never()).logout(any(), any(), any());
    }

    @Test
    void logout_shouldUseAuthenticatedIdentityInsteadOfRequestBody() {
        UUID sessionId = UUID.randomUUID();
        when(authorizationService.getCurrentUser())
                .thenReturn(new UserContext(1L, "USER", "user@test.com"));
        when(authorizationService.getCurrentSessionId()).thenReturn(sessionId);
        LogoutRequest request = new LogoutRequest();
        request.setRefreshToken("refresh");

        controller.logout(request);

        verify(authService).logout(1L, sessionId, "refresh");
    }

    @Test
    void logoutAll_shouldUseAuthenticatedIdentity() {
        when(authorizationService.getCurrentUser())
                .thenReturn(new UserContext(7L, "USER", "user@test.com"));

        controller.logoutAll();

        verify(authService).logoutAll(7L);
    }

    @Test
    void listSessions_shouldUseAuthenticatedIdentity() {
        UUID sessionId = UUID.randomUUID();
        when(authorizationService.getCurrentUser())
                .thenReturn(new UserContext(7L, "USER", "user@test.com"));
        when(authorizationService.getCurrentSessionId()).thenReturn(sessionId);
        when(authService.listSessions(7L, sessionId)).thenReturn(java.util.List.of());

        controller.listSessions();

        verify(authService).listSessions(7L, sessionId);
    }

    @Test
    void revokeSession_shouldScopeToAuthenticatedUser() {
        UUID sessionId = UUID.randomUUID();
        when(authorizationService.getCurrentUser())
                .thenReturn(new UserContext(7L, "USER", "user@test.com"));

        controller.revokeSession(sessionId.toString());

        verify(authService).revokeSession(7L, sessionId);
    }

    @Test
    void revokeSession_shouldRejectMalformedSessionId() {
        when(authorizationService.getCurrentUser())
                .thenReturn(new UserContext(7L, "USER", "user@test.com"));

        assertThrows(BadRequestException.class,
                () -> controller.revokeSession("not-a-uuid"));

        verify(authService, never()).revokeSession(any(), any());
    }

    @Test
    void changePassword_shouldUseAuthenticatedIdentity() {
        when(authorizationService.getCurrentUser())
                .thenReturn(new UserContext(7L, "USER", "user@test.com"));
        var request = new org.example.helptreeservice.dto.auth.ChangePasswordRequest();
        request.setCurrentPassword("Current1");
        request.setNewPassword("NewStrong1");

        controller.changePassword(request);

        verify(authService).changePassword(7L, "Current1", "NewStrong1");
    }

    @Test
    void requestEmailChange_shouldUseAuthenticatedIdentity() {
        when(authorizationService.getCurrentUser())
                .thenReturn(new UserContext(7L, "USER", "user@test.com"));
        var request = new org.example.helptreeservice.dto.auth.EmailChangeRequest();
        request.setNewEmail("new@test.com");
        request.setPassword("Current1");

        controller.requestEmailChange(request);

        verify(authService).requestEmailChange(7L, "new@test.com", "Current1");
    }

    @Test
    void requestPasswordReset_shouldAlwaysReturnAcceptedMessage() {
        var request = new org.example.helptreeservice.dto.auth.PasswordResetRequest();
        request.setEmail("missing@test.com");

        var response = controller.requestPasswordReset(request);

        verify(authService).requestPasswordReset("missing@test.com");
        org.junit.jupiter.api.Assertions.assertEquals(202, response.getStatusCode().value());
        org.junit.jupiter.api.Assertions.assertNotNull(response.getBody());
    }

    @Test
    void confirmPasswordReset_shouldDelegateToken() {
        var request = new org.example.helptreeservice.dto.auth.PasswordResetConfirmRequest();
        request.setToken("raw-token");
        request.setNewPassword("NewStrong1");

        controller.confirmPasswordReset(request);

        verify(authService).confirmPasswordReset("raw-token", "NewStrong1");
    }

    @Test
    void confirmEmailVerification_shouldDelegateTokenOnly() {
        var request = new org.example.helptreeservice.dto.auth.EmailVerificationConfirmRequest();
        request.setToken("raw-token");

        controller.confirmEmailVerification(request);

        verify(authService).confirmEmailVerification("raw-token");
        verify(authService, never()).requestEmailVerification(any());
    }

    @Test
    void confirmEmailChange_shouldDelegateTokenOnly() {
        var request = new org.example.helptreeservice.dto.auth.EmailChangeConfirmRequest();
        request.setToken("raw-token");

        controller.confirmEmailChange(request);

        verify(authService).confirmEmailChange("raw-token");
        verify(authService, never()).requestEmailChange(any(), anyString(), anyString());
    }
}
