package org.example.helptreeservice.security;

import io.jsonwebtoken.Claims;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AuthSessionService;
import org.example.helptreeservice.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketAuthInterceptorTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private AuthSessionService authSessionService;
    @Mock
    private WebSocketHandler webSocketHandler;

    private WebSocketAuthInterceptor interceptor;

    private static final Long USER_ID = 42L;
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final String TICKET = "ws-ticket";

    @BeforeEach
    void setUp() {
        interceptor = new WebSocketAuthInterceptor(jwtService, authSessionService);
    }

    private ServerHttpRequest request(String ticket) {
        MockHttpServletRequest servletRequest =
                new MockHttpServletRequest("GET", "/ws/chat");
        servletRequest.setScheme("http");
        servletRequest.setServerName("localhost");
        servletRequest.setServerPort(8080);
        if (ticket != null) {
            servletRequest.setQueryString("ticket=" + ticket);
        }
        return new ServletServerHttpRequest(servletRequest);
    }

    private ServerHttpResponse response() {
        return new ServletServerHttpResponse(new MockHttpServletResponse());
    }

    private Claims ticketClaims() {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtService.CLAIM_USER_ID)).thenReturn(USER_ID);
        when(claims.get(JwtService.CLAIM_ROLE, String.class)).thenReturn("USER");
        when(claims.get(JwtService.CLAIM_SESSION_ID, String.class))
                .thenReturn(SESSION_ID.toString());
        when(claims.get(JwtService.CLAIM_TOKEN_VERSION)).thenReturn(3L);
        return claims;
    }

    @Test
    void handshake_shouldRejectMissingTicket() {
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                request(null), response(), webSocketHandler, attributes);

        assertFalse(accepted);
        assertTrue(attributes.isEmpty());
        verifyNoInteractions(jwtService, authSessionService);
    }

    @Test
    void handshake_shouldRejectAccessTokenInsteadOfTicket() {
        when(jwtService.parseWsTicket(TICKET))
                .thenThrow(new UnauthorizedException("not a ticket"));
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                request(TICKET), response(), webSocketHandler, attributes);

        assertFalse(accepted);
        assertTrue(attributes.isEmpty());
        verifyNoInteractions(authSessionService);
    }

    @Test
    void handshake_shouldRejectInvalidDatabaseSession() {
        Claims claims = ticketClaims();
        when(jwtService.parseWsTicket(TICKET)).thenReturn(claims);
        when(authSessionService.validateAccessToken(USER_ID, SESSION_ID.toString(), 3L, "USER"))
                .thenThrow(new UnauthorizedException("revoked"));

        boolean accepted = interceptor.beforeHandshake(
                request(TICKET), response(), webSocketHandler, new HashMap<>());

        assertFalse(accepted);
    }

    @Test
    void handshake_shouldAcceptValidTicketAndBindHandshakeUser() {
        Claims claims = ticketClaims();
        when(jwtService.parseWsTicket(TICKET)).thenReturn(claims);
        when(authSessionService.validateAccessToken(USER_ID, SESSION_ID.toString(), 3L, "USER"))
                .thenReturn(new AuthSessionService.AuthenticatedUser(
                        USER_ID, "user@test.com", "USER", SESSION_ID, 3L));
        Map<String, Object> attributes = new HashMap<>();

        boolean accepted = interceptor.beforeHandshake(
                request(TICKET), response(), webSocketHandler, attributes);

        assertTrue(accepted);
        assertEquals(USER_ID, attributes.get(WebSocketAuthInterceptor.ATTR_USER_ID));
        assertEquals("USER", attributes.get(WebSocketAuthInterceptor.ATTR_ROLE));
        assertEquals(SESSION_ID.toString(),
                attributes.get(WebSocketAuthInterceptor.ATTR_SESSION_ID));
        assertEquals(3L, attributes.get(WebSocketAuthInterceptor.ATTR_TOKEN_VERSION));
    }

    @Test
    void handshake_shouldRejectTicketWithoutSessionClaim() {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtService.CLAIM_USER_ID)).thenReturn(USER_ID);
        when(claims.get(JwtService.CLAIM_ROLE, String.class)).thenReturn("USER");
        when(claims.get(JwtService.CLAIM_SESSION_ID, String.class)).thenReturn(null);
        when(claims.get(JwtService.CLAIM_TOKEN_VERSION)).thenReturn(3L);
        when(jwtService.parseWsTicket(TICKET)).thenReturn(claims);

        boolean accepted = interceptor.beforeHandshake(
                request(TICKET), response(), webSocketHandler, new HashMap<>());

        assertFalse(accepted);
        verifyNoInteractions(authSessionService);
    }
}
