package org.example.helptreeservice.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.AuthConfig;
import org.example.helptreeservice.config.EconomyConfig;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Owns the browser-facing authentication cookies.
 *
 * <p>The refresh token is written only to an HttpOnly cookie scoped to the auth
 * endpoints. The SPA never sees its value. A second, readable cookie carries a
 * random CSRF token that the SPA echoes in a header when refreshing; the server
 * compares the two values. This is a classic double-submit scheme and works
 * because refresh is the only state-changing endpoint that authenticates by a
 * cookie alone — all other writes still require the Bearer access token.</p>
 */
@Service
@RequiredArgsConstructor
public class AuthCookieService {

    private final AuthConfig authConfig;
    private final EconomyConfig economyConfig;

    public String newCsrfToken() {
        return UUID.randomUUID().toString();
    }

    /** Writes both cookies in a single response. Call before the body is committed. */
    public void writeTokens(HttpServletResponse response, String refreshToken, String csrfToken) {
        Duration lifetime = Duration.ofDays(Math.max(1, economyConfig.getRefreshTokenDays()));

        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie
                .from(authConfig.getRefreshCookieName(), refreshToken)
                .httpOnly(true)
                .secure(authConfig.isRefreshCookieSecure())
                .sameSite(authConfig.getRefreshCookieSameSite())
                .path(authConfig.getRefreshCookiePath())
                .maxAge(lifetime)
                .build()
                .toString());

        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie
                .from(authConfig.getCsrfCookieName(), csrfToken)
                .httpOnly(false)
                .secure(authConfig.isRefreshCookieSecure())
                .sameSite(authConfig.getRefreshCookieSameSite())
                .path(authConfig.getCsrfCookiePath())
                .maxAge(lifetime)
                .build()
                .toString());
    }

    /** Expires both cookies on logout. */
    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie
                .from(authConfig.getRefreshCookieName(), "")
                .httpOnly(true)
                .secure(authConfig.isRefreshCookieSecure())
                .sameSite(authConfig.getRefreshCookieSameSite())
                .path(authConfig.getRefreshCookiePath())
                .maxAge(Duration.ZERO)
                .build()
                .toString());

        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie
                .from(authConfig.getCsrfCookieName(), "")
                .httpOnly(false)
                .secure(authConfig.isRefreshCookieSecure())
                .sameSite(authConfig.getRefreshCookieSameSite())
                .path(authConfig.getCsrfCookiePath())
                .maxAge(Duration.ZERO)
                .build()
                .toString());
    }

    public String readRefreshToken(HttpServletRequest request) {
        return readCookie(request, authConfig.getRefreshCookieName());
    }

    public String readCsrfCookie(HttpServletRequest request) {
        return readCookie(request, authConfig.getCsrfCookieName());
    }

    public String csrfHeaderName() {
        return authConfig.getCsrfHeaderName();
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
