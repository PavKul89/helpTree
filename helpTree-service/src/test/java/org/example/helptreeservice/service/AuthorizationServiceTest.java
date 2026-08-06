package org.example.helptreeservice.service;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationServiceTest {

    private final AuthorizationService authService = new AuthorizationService();

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void setupRequest(String userId, String role, String email) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (userId != null) request.setAttribute("X-User-Id", userId);
        if (role != null) request.setAttribute("X-User-Role", role);
        if (email != null) request.setAttribute("X-User-Email", email);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @Test
    void getCurrentUser_shouldReturnNullWhenNoContext() {
        RequestContextHolder.resetRequestAttributes();

        AuthorizationService.UserContext user = authService.getCurrentUser();

        assertNull(user);
    }

    @Test
    void getCurrentUser_shouldReturnNullWhenNoUserId() {
        setupRequest(null, "USER", "test@email.com");

        AuthorizationService.UserContext user = authService.getCurrentUser();

        assertNull(user);
    }

    @Test
    void getCurrentUser_shouldReturnUserWithCorrectData() {
        setupRequest("42", "ADMIN", "admin@test.com");

        AuthorizationService.UserContext user = authService.getCurrentUser();

        assertNotNull(user);
        assertEquals(42L, user.getUserId());
        assertEquals("ADMIN", user.getRole());
        assertEquals("admin@test.com", user.getEmail());
    }

    @Test
    void getCurrentUser_shouldDefaultRoleToUser() {
        setupRequest("1", null, "user@test.com");

        AuthorizationService.UserContext user = authService.getCurrentUser();

        assertNotNull(user);
        assertEquals("USER", user.getRole());
    }

    @Test
    void isAdmin_shouldReturnTrueForAdmin() {
        setupRequest("1", "ADMIN", "admin@test.com");

        assertTrue(authService.isAdmin());
    }

    @Test
    void isAdmin_shouldReturnFalseForUser() {
        setupRequest("1", "USER", "user@test.com");

        assertFalse(authService.isAdmin());
    }

    @Test
    void isAdmin_shouldReturnFalseWhenNoUser() {
        RequestContextHolder.resetRequestAttributes();

        assertFalse(authService.isAdmin());
    }

    @Test
    void isOwner_shouldReturnTrueWhenIdsMatch() {
        setupRequest("5", "USER", "user@test.com");

        assertTrue(authService.isOwner(5L));
    }

    @Test
    void isOwner_shouldReturnFalseWhenIdsDiffer() {
        setupRequest("5", "USER", "user@test.com");

        assertFalse(authService.isOwner(99L));
    }

    @Test
    void canManagePost_shouldReturnTrueForOwner() {
        setupRequest("10", "USER", "user@test.com");

        assertTrue(authService.canManagePost(10L));
    }

    @Test
    void canManagePost_shouldReturnTrueForAdmin() {
        setupRequest("1", "ADMIN", "admin@test.com");

        assertTrue(authService.canManagePost(999L));
    }

    @Test
    void canManagePost_shouldReturnFalseForNonOwner() {
        setupRequest("10", "USER", "user@test.com");

        assertFalse(authService.canManagePost(20L));
    }

    @Test
    void canManagePost_shouldReturnFalseWhenNoUser() {
        RequestContextHolder.resetRequestAttributes();

        assertFalse(authService.canManagePost(1L));
    }

    @Test
    void canManageUser_shouldReturnTrueForOwner() {
        setupRequest("5", "USER", "user@test.com");

        assertTrue(authService.canManageUser(5L));
    }

    @Test
    void canManageUser_shouldReturnTrueForAdmin() {
        setupRequest("1", "ADMIN", "admin@test.com");

        assertTrue(authService.canManageUser(999L));
    }

    @Test
    void canAccessHelp_shouldReturnTrueForHelper() {
        setupRequest("10", "USER", "helper@test.com");

        assertTrue(authService.canAccessHelp(10L, 20L));
    }

    @Test
    void canAccessHelp_shouldReturnTrueForReceiver() {
        setupRequest("20", "USER", "receiver@test.com");

        assertTrue(authService.canAccessHelp(10L, 20L));
    }

    @Test
    void canAccessHelp_shouldReturnFalseForUnrelatedUser() {
        setupRequest("30", "USER", "other@test.com");

        assertFalse(authService.canAccessHelp(10L, 20L));
    }

    @Test
    void canAccessHelp_shouldReturnTrueForAdmin() {
        setupRequest("1", "ADMIN", "admin@test.com");

        assertTrue(authService.canAccessHelp(10L, 20L));
    }
}
