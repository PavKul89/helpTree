package org.example.helptreeservice.service;

import jakarta.servlet.http.Cookie;
import org.example.helptreeservice.config.AuthConfig;
import org.example.helptreeservice.config.EconomyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthCookieServiceTest {

    private final AuthConfig authConfig = new AuthConfig();
    private final EconomyConfig economyConfig = new EconomyConfig();
    private final AuthCookieService service = new AuthCookieService(authConfig, economyConfig);

    @Test
    void refreshCookieIsHttpOnlySecureAndScopedToAuthEndpoints() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.writeTokens(response, "refresh-value", "csrf-value");

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertEquals(2, cookies.size());
        String refresh = cookies.stream()
                .filter(value -> value.startsWith(authConfig.getRefreshCookieName() + "="))
                .findFirst()
                .orElseThrow();
        assertTrue(refresh.contains("HttpOnly"));
        assertTrue(refresh.contains("Secure"));
        assertTrue(refresh.contains("SameSite=Strict"));
        assertTrue(refresh.contains("Path=/api/auth"));
        assertTrue(refresh.contains("Max-Age=" + 7L * 24 * 60 * 60));
        assertTrue(refresh.contains("refresh-value"));

        String csrf = cookies.stream()
                .filter(value -> value.startsWith(authConfig.getCsrfCookieName() + "="))
                .findFirst()
                .orElseThrow();
        assertTrue(csrf.contains("csrf-value"));
        assertFalse(csrf.contains("HttpOnly"));
        assertTrue(csrf.contains("Path=/"));
    }

    @Test
    void clearExpiresBothCookies() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clear(response);

        List<String> cookies = response.getHeaders("Set-Cookie");
        assertEquals(2, cookies.size());
        assertTrue(cookies.stream().allMatch(value -> value.contains("Max-Age=0")));
        assertTrue(cookies.stream().anyMatch(value -> value.contains("HttpOnly")));
    }

    @Test
    void readsConfiguredCookieNames() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie(authConfig.getRefreshCookieName(), "refresh-value"),
                new Cookie(authConfig.getCsrfCookieName(), "csrf-value"));

        assertEquals("refresh-value", service.readRefreshToken(request));
        assertEquals("csrf-value", service.readCsrfCookie(request));
        assertNotNull(service.newCsrfToken());
    }
}
