package org.example.helptreeservice.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Whitelist for conditional GET (ETag) and {@code Cache-Control}.
 *
 * <p>Only explicitly public, read-only projections are cacheable. Everything
 * else — any non-GET request and any authenticated/business endpoint — passes
 * through untouched. The list is intentionally narrow: a filtering mistake on
 * an authenticated endpoint could let a shared cache serve another user's
 * data.</p>
 *
 * <p>Two policies are used:</p>
 * <ul>
 *   <li><b>anonymous</b> — the response does not depend on the caller, so it may
 *       be stored by a shared cache for a short time;</li>
 *   <li><b>auth-aware</b> — the same URL returns different visibility for the
 *       owner/admin, so only a private cache may store it and it must
 *       revalidate every time. ETag still saves the body transfer.</li>
 * </ul>
 */
@Component
public class PublicCachePolicy {

    private static final String ANONYMOUS_CACHE_CONTROL =
            "public, max-age=30, stale-while-revalidate=30";
    private static final String AUTH_AWARE_CACHE_CONTROL =
            "private, no-cache";

    /** Responses that never depend on the caller's identity. */
    private static final List<Pattern> ANONYMOUS_PATHS = List.of(
            Pattern.compile("^/api/posts(?:/.*)?$"),
            Pattern.compile("^/api/stats$"),
            Pattern.compile("^/api/search$"),
            Pattern.compile("^/api/users/public$"),
            Pattern.compile("^/api/helps/graph$"),
            Pattern.compile("^/api/helps/stats$"),
            Pattern.compile("^/api/reviews(?:/.*)?$"),
            Pattern.compile("^/api/ratings(?:/.*)?$"),
            Pattern.compile("^/api/achievements$")
    );

    /** Same URL, but visibility depends on the owner/admin role. */
    private static final List<Pattern> AUTH_AWARE_PATHS = List.of(
            Pattern.compile("^/api/users/\\d+/public$"),
            Pattern.compile("^/api/activities$"),
            Pattern.compile("^/api/users/\\d+/activities$")
    );

    public boolean isCacheable(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        return isCacheablePath(pathOf(request));
    }

    boolean isCacheablePath(String path) {
        return AUTH_AWARE_PATHS.stream().anyMatch(pattern -> pattern.matcher(path).matches())
                || ANONYMOUS_PATHS.stream().anyMatch(pattern -> pattern.matcher(path).matches());
    }

    public boolean isAuthAware(HttpServletRequest request) {
        return AUTH_AWARE_PATHS.stream()
                .anyMatch(pattern -> pattern.matcher(pathOf(request)).matches());
    }

    public String cacheControl(HttpServletRequest request) {
        return isAuthAware(request) ? AUTH_AWARE_CACHE_CONTROL : ANONYMOUS_CACHE_CONTROL;
    }

    private String pathOf(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }
}
