package org.example.helptreeservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.RateLimitConfig;
import org.example.helptreeservice.enums.ErrorType;
import org.example.helptreeservice.security.ClientInfoResolver;
import org.example.helptreeservice.service.ratelimit.DistributedRateLimiter;
import org.example.helptreeservice.service.ratelimit.RateLimitDecision;
import org.example.helptreeservice.util.ApiErrorWriter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Fast, distributed rate limiter.
 *
 * <p>Counters live in the shared database (see {@code JdbcRateLimitStore}) and
 * are keyed by route, client IP and authenticated account. The account part is
 * essential: several users behind one NAT or reverse proxy do not share a
 * bucket once they are authenticated. Anonymous routes (login, registration)
 * fall back to the IP part only.</p>
 *
 * <p>Forwarded headers are read through {@link ClientInfoResolver} and are only
 * trusted when {@code app.auth.trust-forwarded-headers} is enabled. The proxy
 * must overwrite, not blindly append to, {@code X-Forwarded-For}; otherwise a
 * client can choose its own key.</p>
 */
@Slf4j
@Component
public class RateLimiterFilter extends OncePerRequestFilter {

    private static final Pattern UUID_SEGMENT = Pattern.compile(
            "(?<=/)[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?=/|$)");
    private static final Pattern NUMERIC_SEGMENT = Pattern.compile("(?<=/)\\d+(?=/|$)");

    private final RateLimitConfig config;
    private final DistributedRateLimiter rateLimiter;
    private final ClientInfoResolver clientInfoResolver;

    public RateLimiterFilter(RateLimitConfig config,
                             DistributedRateLimiter rateLimiter,
                             ClientInfoResolver clientInfoResolver) {
        this.config = config;
        this.rateLimiter = rateLimiter;
        this.clientInfoResolver = clientInfoResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        if (!config.isEnabled() || shouldSkip(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        RateLimitRule rule = resolveRule(request);

        String ip = clientInfoResolver.resolveIp(request);
        if (ip == null || ip.isBlank()) {
            ip = "unknown";
        }
        String account = resolveAccount(request);
        String key = "rl:" + rule.route() + ":" + ip + ":" + account;

        RateLimitDecision decision;
        try {
            decision = rateLimiter.acquire(
                    key, rule.limit(), Duration.ofSeconds(rule.windowSeconds()));
        } catch (RuntimeException ex) {
            // Never turn a store outage into a total API outage. The counter is
            // lost for this request only and the error is visible in logs.
            log.error("Rate limiting store is unavailable for {}; request allowed", path, ex);
            filterChain.doFilter(request, response);
            return;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(decision.retryAfterSeconds()));

        if (decision.allowed()) {
            filterChain.doFilter(request, response);
            return;
        }

        log.warn("Превышен лимит запросов: route={}, ip={}, limit={}",
                rule.route(), ip, rule.limit());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        ApiErrorWriter.write(request, response, HttpStatus.TOO_MANY_REQUESTS,
                ErrorType.TOO_MANY_REQUESTS, "Превышен лимит запросов. Попробуйте позже.");
    }

    private boolean shouldSkip(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "OPTIONS".equalsIgnoreCase(request.getMethod())
                || path.startsWith("/actuator")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/ws/")
                || path.equals("/error")
                || path.equals("/favicon.ico");
    }

    private RateLimitRule resolveRule(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String path = normalizePath(request.getRequestURI());
        int window = Math.max(1, config.getWindowSeconds());

        if ("POST".equals(method)) {
            if ("/api/auth/login".equals(path)) {
                return new RateLimitRule("auth.login", config.getLoginLimit(), window);
            }
            if ("/api/auth/register".equals(path)) {
                return new RateLimitRule("auth.register", config.getRegisterLimit(), window);
            }
            if (path.startsWith("/api/auth/password/reset/")
                    || path.startsWith("/api/auth/email/")) {
                return new RateLimitRule("auth.token", config.getPasswordResetLimit(), window);
            }
            if (isUploadPath(path)) {
                return new RateLimitRule("upload", config.getUploadLimit(), window);
            }
        }

        if (path.startsWith("/api/auth/")) {
            return new RateLimitRule("auth", config.getAuthLimit(), window);
        }

        if (isWrite(method) && isSocialWritePath(path)) {
            if (isUploadPath(path)) {
                return new RateLimitRule("upload", config.getUploadLimit(), window);
            }
            return new RateLimitRule("write:" + path, config.getCreateLimit(), window);
        }

        // Every other route still gets its own bucket, so a noisy read endpoint
        // cannot exhaust the budget of an unrelated one.
        return new RateLimitRule("route:" + method + ":" + path, config.getDefaultLimit(), window);
    }

    private boolean isWrite(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private boolean isUploadPath(String path) {
        return path.equals("/api/images")
                || path.equals("/api/images/multiple")
                || path.matches("/api/users/\\d+/avatar");
    }

    private boolean isSocialWritePath(String path) {
        return path.startsWith("/api/posts")
                || path.startsWith("/api/comments")
                || path.startsWith("/api/helps")
                || path.startsWith("/api/reviews")
                || path.startsWith("/api/chats")
                || path.startsWith("/api/reports")
                || path.startsWith("/api/users");
    }

    private String resolveAccount(HttpServletRequest request) {
        Object userId = request.getAttribute("X-User-Id");
        return userId == null ? "anon" : "u" + userId;
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = UUID_SEGMENT.matcher(path).replaceAll("{id}");
        return NUMERIC_SEGMENT.matcher(normalized).replaceAll("{id}");
    }

    private record RateLimitRule(String route, int limit, int windowSeconds) {
    }
}
