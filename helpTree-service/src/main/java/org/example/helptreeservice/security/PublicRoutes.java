package org.example.helptreeservice.security;

import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * The single registry of endpoints that are reachable without an access token.
 *
 * <p>Both {@link JwtAuthFilter} (which decides whether a missing/invalid token
 * is fatal) and {@link SecurityConfig} (which builds the MVC authorization
 * chain) must agree on this list. Keeping it here prevents a route from being
 * permitted by one layer while the other still returns 401/403.</p>
 */
public final class PublicRoutes {

    private static final Set<String> AUTH_POST_PATHS = Set.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/password/reset/request",
            "/api/auth/password/reset/confirm",
            "/api/auth/email/verification/confirm",
            "/api/auth/email/change/confirm"
    );

    private static final Set<String> EXACT_PATHS = Set.of(
            "/actuator/info",
            "/swagger-ui.html",
            "/api/achievements",
            "/api/activities"
    );

    private static final Pattern POST_VIEW = Pattern.compile("/api/posts/\\d+/views");
    private static final Pattern USER_PUBLIC = Pattern.compile("/api/users/\\d+/public");
    private static final Pattern USER_ACTIVITIES = Pattern.compile("/api/users/\\d+/activities");
    private static final Pattern RATING_USER = Pattern.compile("/api/ratings/user/\\d+");

    private PublicRoutes() {
    }

    public static boolean isPublic(String path, String method) {
        if (path == null) {
            return false;
        }
        if (path.startsWith("/actuator/health")
                && ("GET".equals(method) || "HEAD".equals(method))) {
            return true;
        }
        if (EXACT_PATHS.contains(path)) {
            return true;
        }
        if ("POST".equals(method) && AUTH_POST_PATHS.contains(path)) {
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
        // the anonymous viewer key and the endpoint exposes no personal data.
        if ("POST".equals(method) && POST_VIEW.matcher(path).matches()) {
            return true;
        }
        if ("GET".equals(method)) {
            // `/api/posts/mine` is an authenticated owner view even though it
            // shares the public `/api/posts/**` prefix.
            if (path.equals("/api/posts")
                    || (path.startsWith("/api/posts/") && !path.equals("/api/posts/mine"))) {
                return true;
            }
            if (path.equals("/api/stats")
                    || path.equals("/api/search")
                    || path.equals("/api/users/public")
                    || path.equals("/api/helps/graph")
                    || path.equals("/api/helps/stats")) {
                return true;
            }
            if (USER_PUBLIC.matcher(path).matches() || USER_ACTIVITIES.matcher(path).matches()) {
                return true;
            }
            if (path.startsWith("/api/reviews/")) {
                return true;
            }
            // Only the aggregate rating and one user's rating are public. The
            // calculation history and recalculation require authentication.
            if (path.equals("/api/ratings/top") || RATING_USER.matcher(path).matches()) {
                return true;
            }
        }
        return false;
    }

    /** Adapter used by {@link SecurityConfig} so both layers read the same list. */
    public static final RequestMatcher REQUEST_MATCHER =
            request -> isPublic(request.getRequestURI(), request.getMethod());
}
