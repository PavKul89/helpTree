package org.example.helptreeservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.enums.ErrorType;
import org.example.helptreeservice.service.AuthCookieService;
import org.example.helptreeservice.util.ApiErrorWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * CSRF protection for the one endpoint that authenticates by cookie alone.
 *
 * <p>Every other state-changing route still requires the Bearer access token,
 * which a cross-site page cannot forge. Refresh is different: the browser
 * attaches the HttpOnly cookie automatically. We therefore require a random
 * double-submit token (readable cookie + matching request header) and, when the
 * browser sends one, an Origin that is on the configured allowlist.</p>
 *
 * <p>The filter is deliberately narrow: non-browser clients that send the
 * refresh token explicitly in the request body do not carry the cookie and are
 * not affected. An attacker cannot exploit that path because the refresh token
 * is never available to JavaScript.</p>
 */
@Component
@RequiredArgsConstructor
public class RefreshCsrfFilter extends OncePerRequestFilter {

    private static final String REFRESH_PATH = "/api/auth/refresh";

    private final AuthCookieService authCookieService;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://localhost:5173}")
    private String allowedOrigins;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        boolean isRefresh = "POST".equalsIgnoreCase(request.getMethod())
                && REFRESH_PATH.equals(request.getRequestURI());
        // Only cookie-authenticated refreshes need CSRF protection.
        return !isRefresh || authCookieService.readRefreshToken(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin != null && !origin.isBlank() && !isAllowedOrigin(origin)) {
            reject(request, response);
            return;
        }

        String cookieToken = authCookieService.readCsrfCookie(request);
        String headerToken = request.getHeader(authCookieService.csrfHeaderName());
        if (cookieToken == null || cookieToken.isBlank()
                || headerToken == null || headerToken.isBlank()
                || !constantTimeEquals(cookieToken, headerToken)) {
            reject(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isAllowedOrigin(String origin) {
        String candidate = normalize(origin);
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(this::normalize)
                .anyMatch(candidate::equals);
    }

    private String normalize(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        byte[] left = expected.getBytes(StandardCharsets.UTF_8);
        byte[] right = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ApiErrorWriter.write(request, response, HttpStatus.FORBIDDEN, ErrorType.FORBIDDEN,
                "Отсутствует или неверен CSRF-токен");
    }
}
