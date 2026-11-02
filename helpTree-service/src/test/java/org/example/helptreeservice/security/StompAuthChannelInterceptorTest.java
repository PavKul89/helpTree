package org.example.helptreeservice.security;

import io.jsonwebtoken.Claims;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AuthSessionService;
import org.example.helptreeservice.service.AuthSessionService.AuthenticatedUser;
import org.example.helptreeservice.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private AuthSessionService authSessionService;

    private StompAuthChannelInterceptor interceptor;

    private static final Long USER_ID = 42L;
    private static final String TOKEN = "access-token";
    private static final UUID SESSION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(jwtService, authSessionService);
    }

    private Message<byte[]> connectMessage(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> frame(StompCommand command, WsUserPrincipal principal) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (principal != null) {
            accessor.setUser(principal);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Claims claims(String role, String sessionId, Long tokenVersion) {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtService.CLAIM_USER_ID)).thenReturn(USER_ID);
        when(claims.get(JwtService.CLAIM_ROLE, String.class)).thenReturn(role);
        when(claims.get(JwtService.CLAIM_SESSION_ID, String.class)).thenReturn(sessionId);
        when(claims.get(JwtService.CLAIM_TOKEN_VERSION)).thenReturn(tokenVersion);
        return claims;
    }

    @Test
    void connect_shouldRejectMissingAuthorizationHeader() {
        assertNull(interceptor.preSend(connectMessage(null), null));
        verifyNoInteractions(jwtService, authSessionService);
    }

    @Test
    void connect_shouldRejectNonBearerAuthorizationHeader() {
        assertNull(interceptor.preSend(connectMessage("Basic abc"), null));
        verifyNoInteractions(jwtService, authSessionService);
    }

    @Test
    void connect_shouldAcceptValidDatabaseBackedToken() {
        Claims tokenClaims = claims("USER", SESSION_ID.toString(), 3L);
        when(jwtService.parseClaims(TOKEN)).thenReturn(tokenClaims);
        when(authSessionService.validateAccessToken(
                USER_ID, SESSION_ID.toString(), 3L, "USER"))
                .thenReturn(new AuthenticatedUser(USER_ID, "user@test.com", "USER",
                        SESSION_ID, 3L));

        Message<?> result = interceptor.preSend(
                connectMessage("Bearer " + TOKEN), null);

        assertNotNull(result);
        WsUserPrincipal principal = assertInstanceOf(
                WsUserPrincipal.class, StompHeaderAccessor.wrap(result).getUser());
        assertEquals(USER_ID, principal.getUserId());
        assertEquals("USER", principal.getRole());
        assertEquals(SESSION_ID, principal.getSessionId());
        assertEquals(3L, principal.getTokenVersion());
        verify(authSessionService).validateAccessToken(
                USER_ID, SESSION_ID.toString(), 3L, "USER");
    }

    @Test
    void connect_shouldRejectInvalidSignatureOrExpiredToken() {
        when(jwtService.parseClaims(TOKEN))
                .thenThrow(new IllegalArgumentException("invalid token"));

        assertNull(interceptor.preSend(connectMessage("Bearer " + TOKEN), null));
        verifyNoInteractions(authSessionService);
    }

    @Test
    void connect_shouldRejectRevokedOrDePrivilegedSession() {
        Claims tokenClaims = claims("ADMIN", SESSION_ID.toString(), 0L);
        when(jwtService.parseClaims(TOKEN)).thenReturn(tokenClaims);
        when(authSessionService.validateAccessToken(
                USER_ID, SESSION_ID.toString(), 0L, "ADMIN"))
                .thenThrow(new UnauthorizedException("stale role"));

        assertNull(interceptor.preSend(connectMessage("Bearer " + TOKEN), null));
    }

    @Test
    void connect_shouldRejectTokenWithoutSessionClaim() {
        Claims tokenClaims = claims("USER", null, 0L);
        when(jwtService.parseClaims(TOKEN)).thenReturn(tokenClaims);

        assertNull(interceptor.preSend(connectMessage("Bearer " + TOKEN), null));
        verifyNoInteractions(authSessionService);
    }

    @Test
    void send_shouldRejectFrameWithoutAuthenticatedPrincipal() {
        assertNull(interceptor.preSend(frame(StompCommand.SEND, null), null));
        verifyNoInteractions(jwtService, authSessionService);
    }

    @Test
    void send_shouldRevalidateSessionBeforeAcceptingFrame() {
        WsUserPrincipal principal = new WsUserPrincipal(USER_ID, "USER", SESSION_ID, 0L);
        when(authSessionService.validateAccessToken(
                USER_ID, SESSION_ID.toString(), 0L, "USER"))
                .thenReturn(new AuthenticatedUser(USER_ID, "user@test.com", "USER",
                        SESSION_ID, 0L));

        Message<byte[]> message = frame(StompCommand.SEND, principal);

        assertEquals(message, interceptor.preSend(message, null));
    }

    @Test
    void send_shouldDropFrameAfterSessionRevocationOrRoleChange() {
        WsUserPrincipal principal = new WsUserPrincipal(USER_ID, "ADMIN", SESSION_ID, 0L);
        when(authSessionService.validateAccessToken(
                USER_ID, SESSION_ID.toString(), 0L, "ADMIN"))
                .thenThrow(new UnauthorizedException("role changed"));

        assertNull(interceptor.preSend(frame(StompCommand.SEND, principal), null));
    }

    @Test
    void subscribe_shouldAlsoRevalidateSession() {
        WsUserPrincipal principal = new WsUserPrincipal(USER_ID, "USER", SESSION_ID, 0L);
        when(authSessionService.validateAccessToken(
                USER_ID, SESSION_ID.toString(), 0L, "USER"))
                .thenThrow(new UnauthorizedException("revoked"));

        assertNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE, principal), null));
    }
}
