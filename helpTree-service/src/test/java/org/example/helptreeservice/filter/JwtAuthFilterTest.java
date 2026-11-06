package org.example.helptreeservice.filter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import org.example.helptreeservice.exception.AccountBlockedException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.security.JwtAuthFilter;
import org.example.helptreeservice.service.AuthSessionService;
import org.example.helptreeservice.service.AuthSessionService.AuthenticatedUser;
import org.example.helptreeservice.service.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthSessionService authSessionService;

    @Mock
    private FilterChain filterChain;

    private static final String TOKEN = "valid-access-token";
    private static final Long USER_ID = 42L;
    private static final String EMAIL = "user@test.com";
    private static final String SESSION_ID = UUID.randomUUID().toString();
    private static final long TOKEN_VERSION = 7L;

    private void stubValidToken(String role, String email) {
        Claims claims = mock(Claims.class);
        when(jwtService.parseAccessClaims(TOKEN)).thenReturn(claims);
        when(claims.get(JwtService.CLAIM_USER_ID)).thenReturn(USER_ID);
        when(claims.get(JwtService.CLAIM_ROLE, String.class)).thenReturn(role);
        when(claims.get(JwtService.CLAIM_SESSION_ID, String.class)).thenReturn(SESSION_ID);
        when(claims.get(JwtService.CLAIM_TOKEN_VERSION)).thenReturn(TOKEN_VERSION);
        when(authSessionService.validateAccessToken(USER_ID, SESSION_ID, TOKEN_VERSION, role, false))
                .thenReturn(new AuthenticatedUser(USER_ID, email, role,
                        UUID.fromString(SESSION_ID), TOKEN_VERSION));
    }

    private MockHttpServletResponse invoke(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        Method method = JwtAuthFilter.class.getDeclaredMethod("doFilterInternal",
                jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class,
                FilterChain.class);
        method.setAccessible(true);
        method.invoke(jwtAuthFilter, request, response, filterChain);
        return response;
    }

    private boolean isPublic(String path, String method) throws Exception {
        Method publicMethod = JwtAuthFilter.class.getDeclaredMethod("isPublicPath", String.class, String.class);
        publicMethod.setAccessible(true);
        return (Boolean) publicMethod.invoke(jwtAuthFilter, path, method);
    }

    @Test
    void isPublicPath_shouldAllowOnlyExplicitAuthEndpoints() throws Exception {
        assertTrue(isPublic("/api/auth/login", "POST"));
        assertTrue(isPublic("/api/auth/register", "POST"));
        assertTrue(isPublic("/api/auth/refresh", "POST"));
        assertTrue(isPublic("/api/auth/password/reset/request", "POST"));
        assertTrue(isPublic("/api/auth/password/reset/confirm", "POST"));
        assertTrue(isPublic("/api/auth/email/verification/confirm", "POST"));
        assertTrue(isPublic("/api/auth/email/change/confirm", "POST"));

        assertFalse(isPublic("/api/auth/logout", "POST"));
        assertFalse(isPublic("/api/auth/logout-all", "POST"));
        assertFalse(isPublic("/api/auth/sessions", "GET"));
        assertFalse(isPublic("/api/auth/password/change", "POST"));
        assertFalse(isPublic("/api/auth/email/change", "POST"));
        assertFalse(isPublic("/api/auth/anything-else", "GET"));
    }

    @Test
    void isPublicPath_shouldAllowPublicReadEndpoints() throws Exception {
        assertTrue(isPublic("/api/posts", "GET"));
        assertTrue(isPublic("/api/posts/1", "GET"));
        assertTrue(isPublic("/api/posts/user/7", "GET"));
        assertTrue(isPublic("/api/posts/7/comments", "GET"));
        assertTrue(isPublic("/api/posts/map", "GET"));
        assertTrue(isPublic("/api/users/public", "GET"));
        assertTrue(isPublic("/api/users/7/public", "GET"));
        assertTrue(isPublic("/api/users/7/activities", "GET"));
        assertTrue(isPublic("/api/activities", "GET"));
        assertTrue(isPublic("/api/ratings/top", "GET"));
        assertTrue(isPublic("/api/ratings/user/7", "GET"));
        assertTrue(isPublic("/api/helps/graph", "GET"));
        assertFalse(isPublic("/api/actuator", "GET"));

        // The owner view and internal rating history are never public.
        assertFalse(isPublic("/api/posts/mine", "GET"));
        assertFalse(isPublic("/api/ratings/user/7/history", "GET"));
        assertFalse(isPublic("/api/ratings/user/7/recalculate", "GET"));

        assertFalse(isPublic("/api/posts", "POST"));
        assertFalse(isPublic("/api/posts/1", "PUT"));
        assertFalse(isPublic("/api/posts/1", "DELETE"));
        assertFalse(isPublic("/api/posts/by-ids", "POST"));
    }

    @Test
    void isPublicPath_shouldAllowInfrastructurePaths() throws Exception {
        assertTrue(isPublic("/actuator/health", "GET"));
        assertTrue(isPublic("/actuator/health/liveness", "GET"));
        assertTrue(isPublic("/actuator/health/readiness", "GET"));
        assertTrue(isPublic("/ws/chat", "GET"));
        assertTrue(isPublic("/swagger-ui/index.html", "GET"));
        assertFalse(isPublic("/actuator/env", "GET"));
        assertFalse(isPublic("/actuator/health", "POST"));
    }

    @Test
    void filter_shouldSetContextForValidTokenOnPublicPath() throws Exception {
        stubValidToken("USER", EMAIL);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts/user/42");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        MockHttpServletResponse response = invoke(request);

        assertEquals("42", request.getAttribute("X-User-Id"));
        assertEquals("USER", request.getAttribute("X-User-Role"));
        assertEquals(SESSION_ID, request.getAttribute("X-Session-Id"));
        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void filter_shouldIgnoreInvalidTokenOnPublicPath() throws Exception {
        when(jwtService.parseAccessClaims("bad-token")).thenThrow(new IllegalArgumentException("bad"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts/map");
        request.addHeader("Authorization", "Bearer bad-token");

        MockHttpServletResponse response = invoke(request);

        assertNull(request.getAttribute("X-User-Id"));
        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void filter_shouldIgnoreRevokedSessionOnPublicPath() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtService.parseAccessClaims(TOKEN)).thenReturn(claims);
        when(claims.get(JwtService.CLAIM_USER_ID)).thenReturn(USER_ID);
        when(claims.get(JwtService.CLAIM_ROLE, String.class)).thenReturn("USER");
        when(claims.get(JwtService.CLAIM_SESSION_ID, String.class)).thenReturn(SESSION_ID);
        when(claims.get(JwtService.CLAIM_TOKEN_VERSION)).thenReturn(TOKEN_VERSION);
        when(authSessionService.validateAccessToken(USER_ID, SESSION_ID, TOKEN_VERSION, "USER", false))
                .thenThrow(new UnauthorizedException("revoked"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts/1");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        MockHttpServletResponse response = invoke(request);

        assertNull(request.getAttribute("X-User-Id"));
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void filter_shouldRejectInvalidTokenOnProtectedPath() throws Exception {
        when(jwtService.parseAccessClaims("bad-token")).thenThrow(new IllegalArgumentException("bad"));
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/posts/1");
        request.addHeader("Authorization", "Bearer bad-token");

        MockHttpServletResponse response = invoke(request);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(filterChain);
    }

    @Test
    void filter_shouldRejectRequestWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/posts/1");

        MockHttpServletResponse response = invoke(request);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(filterChain);
    }

    @Test
    void filter_shouldPassPublicPathsWithoutToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");

        MockHttpServletResponse response = invoke(request);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void filter_shouldSetAttributesForValidToken() throws Exception {
        stubValidToken("USER", EMAIL);
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/posts/1");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        MockHttpServletResponse response = invoke(request);

        assertEquals("42", request.getAttribute("X-User-Id"));
        assertEquals("USER", request.getAttribute("X-User-Role"));
        assertEquals(EMAIL, request.getAttribute("X-User-Email"));
        assertEquals(SESSION_ID, request.getAttribute("X-Session-Id"));
        verify(authSessionService).validateAccessToken(USER_ID, SESSION_ID, TOKEN_VERSION, "USER", false);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void filter_shouldAllowAuthenticatedImageUpload() throws Exception {
        stubValidToken("USER", EMAIL);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/images/multiple");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        MockHttpServletResponse response = invoke(request);

        assertEquals("42", request.getAttribute("X-User-Id"));
        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void filter_shouldUseDatabaseRoleForInternalPaths() throws Exception {
        stubValidToken("USER", EMAIL);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/users");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        MockHttpServletResponse response = invoke(request);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(filterChain);
    }

    @Test
    void filter_shouldAllowAdminOnInternalPaths() throws Exception {
        stubValidToken("ADMIN", "admin@test.com");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/users");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        MockHttpServletResponse response = invoke(request);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void filter_shouldReturn403ForBlockedUser() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtService.parseAccessClaims(TOKEN)).thenReturn(claims);
        when(claims.get(JwtService.CLAIM_USER_ID)).thenReturn(USER_ID);
        when(claims.get(JwtService.CLAIM_ROLE, String.class)).thenReturn("USER");
        when(claims.get(JwtService.CLAIM_SESSION_ID, String.class)).thenReturn(SESSION_ID);
        when(claims.get(JwtService.CLAIM_TOKEN_VERSION)).thenReturn(TOKEN_VERSION);
        when(authSessionService.validateAccessToken(USER_ID, SESSION_ID, TOKEN_VERSION, "USER", false))
                .thenThrow(new AccountBlockedException("blocked"));

        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/posts/1");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        MockHttpServletResponse response = invoke(request);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(filterChain);
    }

    @Test
    void filter_shouldAllowBlockedUserToBuyAccountUnblock() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtService.parseAccessClaims(TOKEN)).thenReturn(claims);
        when(claims.get(JwtService.CLAIM_USER_ID)).thenReturn(USER_ID);
        when(claims.get(JwtService.CLAIM_ROLE, String.class)).thenReturn("USER");
        when(claims.get(JwtService.CLAIM_SESSION_ID, String.class)).thenReturn(SESSION_ID);
        when(claims.get(JwtService.CLAIM_TOKEN_VERSION)).thenReturn(TOKEN_VERSION);
        when(authSessionService.validateAccessToken(USER_ID, SESSION_ID, TOKEN_VERSION, "USER", true))
                .thenReturn(new AuthenticatedUser(USER_ID, EMAIL, "USER",
                        UUID.fromString(SESSION_ID), TOKEN_VERSION));

        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/users/" + USER_ID + "/wallet/spend");
        request.setParameter("type", "ACCOUNT_UNBLOCK");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        MockHttpServletResponse response = invoke(request);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void filter_shouldAllowBlockedUserToReadOwnProfile() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtService.parseAccessClaims(TOKEN)).thenReturn(claims);
        when(claims.get(JwtService.CLAIM_USER_ID)).thenReturn(USER_ID);
        when(claims.get(JwtService.CLAIM_ROLE, String.class)).thenReturn("USER");
        when(claims.get(JwtService.CLAIM_SESSION_ID, String.class)).thenReturn(SESSION_ID);
        when(claims.get(JwtService.CLAIM_TOKEN_VERSION)).thenReturn(TOKEN_VERSION);
        when(authSessionService.validateAccessToken(USER_ID, SESSION_ID, TOKEN_VERSION, "USER", true))
                .thenReturn(new AuthenticatedUser(USER_ID, EMAIL, "USER",
                        UUID.fromString(SESSION_ID), TOKEN_VERSION));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        MockHttpServletResponse response = invoke(request);

        assertEquals(200, response.getStatus());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void filter_shouldStillRejectBlockedUserForOtherPurchases() throws Exception {
        Claims claims = mock(Claims.class);
        when(jwtService.parseAccessClaims(TOKEN)).thenReturn(claims);
        when(claims.get(JwtService.CLAIM_USER_ID)).thenReturn(USER_ID);
        when(claims.get(JwtService.CLAIM_ROLE, String.class)).thenReturn("USER");
        when(claims.get(JwtService.CLAIM_SESSION_ID, String.class)).thenReturn(SESSION_ID);
        when(claims.get(JwtService.CLAIM_TOKEN_VERSION)).thenReturn(TOKEN_VERSION);
        when(authSessionService.validateAccessToken(USER_ID, SESSION_ID, TOKEN_VERSION, "USER", false))
                .thenThrow(new AccountBlockedException("blocked"));

        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/users/" + USER_ID + "/wallet/spend");
        request.setParameter("type", "VIP");
        request.addHeader("Authorization", "Bearer " + TOKEN);

        MockHttpServletResponse response = invoke(request);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(filterChain);
    }
}
