package org.example.helptreeservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test for the single public-route registry used by both the JWT
 * filter and the Spring Security chain.
 */
class PublicRoutesTest {

    private boolean securityChainAllows(String method, String path) {
        return PublicRoutes.REQUEST_MATCHER.matches(new MockHttpServletRequest(method, path));
    }

    @Test
    void filterAndSecurityChain_shouldUseTheSameDecision() {
        String[][] cases = {
                {"GET", "/api/posts"},
                {"GET", "/api/posts/7"},
                {"GET", "/api/posts/mine"},
                {"POST", "/api/posts/7/views"},
                {"POST", "/api/posts/7"},
                {"GET", "/api/search"},
                {"GET", "/api/stats"},
                {"GET", "/api/users/7/activities"},
                {"GET", "/api/users/7/public"},
                {"GET", "/api/ratings/user/7"},
                {"GET", "/api/ratings/user/7/history"},
                {"GET", "/api/reviews/1"},
                {"POST", "/api/auth/login"},
                {"GET", "/actuator/health"},
                {"GET", "/actuator/prometheus"},
                {"GET", "/api/admin/users"},
                {"PUT", "/internal/users/7/rating"}
        };

        for (String[] testCase : cases) {
            String method = testCase[0];
            String path = testCase[1];
            assertEquals(PublicRoutes.isPublic(path, method), securityChainAllows(method, path),
                    method + " " + path + " must be treated identically by both layers");
        }
    }

    @Test
    void previouslyDivergingGuestRoutes_shouldBePublic() {
        assertTrue(PublicRoutes.isPublic("/api/posts/7/views", "POST"));
        assertTrue(PublicRoutes.isPublic("/api/users/7/activities", "GET"));
        assertTrue(PublicRoutes.isPublic("/api/search", "GET"));
    }

    @Test
    void protectedRoutes_shouldNeverBePublic() {
        assertFalse(PublicRoutes.isPublic("/api/posts/mine", "GET"));
        assertFalse(PublicRoutes.isPublic("/api/ratings/user/7/history", "GET"));
        assertFalse(PublicRoutes.isPublic("/internal/users/7/rating", "PUT"));
        assertFalse(PublicRoutes.isPublic("/api/admin/users", "GET"));
        assertFalse(PublicRoutes.isPublic("/actuator/prometheus", "GET"));
    }
}
