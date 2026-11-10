package org.example.helptreeservice.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.enums.ErrorType;
import org.example.helptreeservice.exception.AccountBlockedException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AuthSessionService;
import org.example.helptreeservice.service.JwtService;
import org.example.helptreeservice.util.ApiErrorWriter;
import org.example.helptreeservice.util.LogContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Authenticates requests with a signed access token and then revalidates it
 * against the database: the user must exist, not be deleted or blocked, the
 * session must still be active and the embedded role/tokenVersion must match the
 * current user row. Old JWTs therefore stop working after a role change, block,
 * deletion, password change or explicit session termination.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AuthSessionService authSessionService;

    private static final Set<String> PUBLIC_AUTH_POST_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/password/reset/request",
            "/api/auth/password/reset/confirm",
            "/api/auth/email/verification/confirm",
            "/api/auth/email/change/confirm"
    );

    private static final Set<String> PUBLIC_EXACT_PATHS = Set.of(
            "/actuator/info",
            "/swagger-ui.html",
            "/api/achievements",
            "/api/activities"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // CORS preflight requests never carry credentials and are handled by the
        // CORS filter before authentication.
        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean publicPath = isPublicPath(path, method);
        String authHeader = request.getHeader("Authorization");
        boolean hasBearerToken = authHeader != null && authHeader.startsWith("Bearer ");

        if (!hasBearerToken) {
            if (publicPath) {
                filterChain.doFilter(request, response);
                return;
            }
            sendError(request, response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED,
                    "Требуется авторизация");
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtService.parseAccessClaims(token);
            Long userId = numberClaim(claims, JwtService.CLAIM_USER_ID);
            String role = claims.get(JwtService.CLAIM_ROLE, String.class);
            String sessionId = claims.get(JwtService.CLAIM_SESSION_ID, String.class);
            Long tokenVersion = numberClaim(claims, JwtService.CLAIM_TOKEN_VERSION);

            if (userId == null) {
                throw new UnauthorizedException("Невалидный токен");
            }

            AuthSessionService.AuthenticatedUser authenticatedUser =
                    authSessionService.validateAccessToken(userId, sessionId, tokenVersion, role,
                            isBlockedSelfServicePath(request, userId));

            if (path.startsWith("/internal/") && !"ADMIN".equals(authenticatedUser.role())) {
                sendError(request, response, HttpStatus.FORBIDDEN, ErrorType.FORBIDDEN,
                        "Только администратор имеет доступ к внутренним эндпоинтам");
                return;
            }

            request.setAttribute("X-User-Id", authenticatedUser.userId().toString());
            request.setAttribute("X-User-Role", authenticatedUser.role());
            request.setAttribute("X-User-Email", authenticatedUser.email() != null ? authenticatedUser.email() : "");
            request.setAttribute("X-Session-Id", authenticatedUser.sessionId().toString());
            LogContext.setUserId(authenticatedUser.userId());

            var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + authenticatedUser.role()));
            var authentication = new UsernamePasswordAuthenticationToken(
                    authenticatedUser.userId(), null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (AccountBlockedException e) {
            if (publicPath) {
                log.debug("Blocked user on public path {}: {}", path, e.getMessage());
                filterChain.doFilter(request, response);
                return;
            }
            sendError(request, response, HttpStatus.FORBIDDEN, ErrorType.ACCOUNT_BLOCKED, e.getMessage());
            return;
        } catch (Exception e) {
            // Public GET endpoints stay accessible without a valid token. A valid
            // token would have populated the owner/admin context above.
            if (publicPath) {
                log.debug("Ignoring invalid token on public path {}: {}", path, e.getMessage());
                filterChain.doFilter(request, response);
                return;
            }
            log.debug("Rejected access token for {}: {}", path, e.getMessage());
            sendError(request, response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED,
                    "Невалидный или истёкший токен");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Long numberClaim(Claims claims, String name) {
        Object value = claims.get(name);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * A debt-blocked account is normally rejected on every protected path. The
     * only exceptions are its own wallet read endpoints and the paid
     * ACCOUNT_UNBLOCK purchase, otherwise the documented recovery flow could
     * never be used. Logout stays available so a blocked session can be ended.
     */
    private boolean isBlockedSelfServicePath(HttpServletRequest request, Long userId) {
        if (userId == null) {
            return false;
        }
        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("POST".equals(method) && "/api/auth/logout".equals(path)) {
            return true;
        }

        // The SPA reads the current profile on startup and needs the blocked
        // flags, otherwise it would log the user out before they reach /wallet.
        if ("GET".equals(method) && "/api/users/me".equals(path)) {
            return true;
        }

        String walletPrefix = "/api/users/" + userId + "/wallet";
        if (!path.startsWith(walletPrefix)) {
            return false;
        }
        if ("GET".equals(method)) {
            return path.equals(walletPrefix)
                    || path.equals(walletPrefix + "/pricing")
                    || path.equals(walletPrefix + "/transactions")
                    || path.equals(walletPrefix + "/transactions/export")
                    || path.equals(walletPrefix + "/paid-features");
        }
        return "POST".equals(method)
                && path.equals(walletPrefix + "/spend")
                && "ACCOUNT_UNBLOCK".equalsIgnoreCase(request.getParameter("type"));
    }

    private boolean isPublicPath(String path, String method) {
        if (path.startsWith("/actuator/health")
                && ("GET".equals(method) || "HEAD".equals(method))) {
            return true;
        }
        if (PUBLIC_EXACT_PATHS.contains(path)) {
            return true;
        }
        if (PUBLIC_AUTH_POST_PATHS.contains(path) && "POST".equals(method)) {
            return true;
        }
        if (path.startsWith("/swagger-ui/")
                || path.equals("/swagger-ui.html")
                || path.equals("/v3/api-docs")
                || path.equals("/v3/api-docs.yaml")
                || path.startsWith("/v3/api-docs/")) {
            return true;
        }
        if (path.startsWith("/ws/")) {
            return true;
        }
        // View counting is available to guests too: the IP + User-Agent hash is
        // the anonymous viewer key. The endpoint itself never exposes data.
        if ("POST".equals(method) && path.matches("/api/posts/\\d+/views")) {
            return true;
        }
        if ("GET".equals(method)) {
            // `/api/posts/mine` is an authenticated owner view even though it
            // shares the public `/api/posts/**` prefix.
            if (path.startsWith("/api/posts/") && !"/api/posts/mine".equals(path)) {
                return true;
            }
            if (path.equals("/api/posts")) {
                return true;
            }
            if (path.equals("/api/stats")
                    || path.equals("/api/users/public")
                    || path.equals("/api/helps/graph")
                    || path.equals("/api/helps/stats")) {
                return true;
            }
            if (path.matches("/api/users/\\d+/public")
                    || path.matches("/api/users/\\d+/activities")) {
                return true;
            }
            if (path.startsWith("/api/reviews/")) {
                return true;
            }
            // Only the aggregate rating and one user's rating are public. The
            // calculation history requires authentication and owner/admin.
            if (path.equals("/api/ratings/top")
                    || path.matches("/api/ratings/user/\\d+")) {
                return true;
            }
        }
        return false;
    }

    private void sendError(HttpServletRequest request,
                           HttpServletResponse response,
                           HttpStatus status,
                           ErrorType type,
                           String message) throws IOException {
        ApiErrorWriter.write(request, response, status, type, message);
    }
}
