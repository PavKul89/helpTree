package org.example.helptreeservice.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Value("${app.jwtSecret}")
    private String jwtSecret;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/reviews/help/",
            "/api/reviews/user/",
            "/api/ratings/",
            "/api/posts/",
            "/api/helps/graph",
            "/api/helps/stats",
            "/api/achievements",
            "/api/activities",
            "/actuator/health"
    );

    private static final Set<String> ADMIN_ONLY_PATHS = Set.of(
            "/api/users"
    );

    private static final Set<String> USER_PATH_EXCEPTIONS = Set.of(
            "/api/users/me",
            "/api/users/telegram",
            "/api/users/current",
            "/api/reviews"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        if (isPublicPath(path, method)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, HttpStatus.UNAUTHORIZED, "Требуется авторизация");
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = claims.get("userId", Long.class);
            String role = claims.get("role", String.class);
            String email = claims.getSubject();

            log.debug("JWT parsed: userId={}, role={}, email={}, claims={}", userId, role, email, claims);

            if (userId == null) {
                log.warn("JWT userId is null, all claims: {}", claims);
                sendError(response, HttpStatus.UNAUTHORIZED, "Требуется авторизация");
                return;
            }

            if (path.startsWith("/internal/") && !"ADMIN".equals(role)) {
                sendError(response, HttpStatus.FORBIDDEN, "Только администратор имеет доступ к внутренним эндпоинтам");
                return;
            }

            if (isAdminOnlyPath(path, method) && !"ADMIN".equals(role)) {
                sendError(response, HttpStatus.FORBIDDEN, "Только администратор имеет доступ");
                return;
            }

            request.setAttribute("X-User-Id", userId.toString());
            request.setAttribute("X-User-Role", role != null ? role : "USER");
            request.setAttribute("X-User-Email", email != null ? email : "");

            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + (role != null ? role : "USER")));
            var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);

        } catch (Exception e) {
            sendError(response, HttpStatus.UNAUTHORIZED, "Невалидный или истёкший токен");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicPath(String path, String method) {
        for (String publicPath : PUBLIC_PATHS) {
            if (path.equals(publicPath)) {
                return true;
            }
        }
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        if (path.startsWith("/actuator/")) {
            return true;
        }
        if (path.startsWith("/api/achievements")) {
            return true;
        }
        if (path.startsWith("/api/activities")) {
            return true;
        }
        if (path.startsWith("/ws/")) {
            return true;
        }
        if ("GET".equals(method) && path.matches("/api/posts(/\\d+)?")) {
            return true;
        }
        if ("GET".equals(method) && path.matches("/api/reviews/help/\\d+")) {
            return true;
        }
        if ("GET".equals(method) && path.matches("/api/reviews/user/\\d+")) {
            return true;
        }
        if ("GET".equals(method) && path.matches("/api/ratings/.*")) {
            return true;
        }
        return false;
    }

    private boolean isAdminOnlyPath(String path, String method) {
        if (!"POST".equals(method) && !"DELETE".equals(method)) {
            return false;
        }

        if ("/api/users".equals(path)) {
            return true;
        }

        if (path.startsWith("/api/achievements")) {
            return false;
        }

        for (String exception : USER_PATH_EXCEPTIONS) {
            if (path.startsWith(exception)) {
                return false;
            }
        }

        if (path.matches("/api/users/\\d+")) {
            return false;
        }

        if (path.matches("/api/users/\\d+/.*")) {
            return false;
        }

        return false;
    }

    private void sendError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String body = objectMapper.writeValueAsString(Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        ));
        response.getWriter().write(body);
    }
}
