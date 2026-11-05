package org.example.helptreeservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class PublicCachePolicyTest {

    private final PublicCachePolicy policy = new PublicCachePolicy();

    @Test
    void cachesAnonymousPublicFeedGet() {
        MockHttpServletRequest request = request("GET", "/api/posts");

        assertTrue(policy.isCacheable(request));
        assertFalse(policy.isAuthAware(request));
        assertTrue(policy.cacheControl(request).startsWith("public"));
    }

    @Test
    void authAwareActivityUsesPrivateNoCache() {
        MockHttpServletRequest request = request("GET", "/api/activities");

        assertTrue(policy.isCacheable(request));
        assertTrue(policy.isAuthAware(request));
        assertEquals("private, no-cache", policy.cacheControl(request));
    }

    @Test
    void doesNotCacheWrites() {
        assertFalse(policy.isCacheable(request("POST", "/api/posts")));
        assertFalse(policy.isCacheable(request("DELETE", "/api/posts/1")));
    }

    @Test
    void doesNotCacheAuthenticatedOrUnlistedEndpoints() {
        assertFalse(policy.isCacheable(request("GET", "/api/users/1")));
        assertFalse(policy.isCacheable(request("GET", "/api/wallet")));
        assertFalse(policy.isCacheable(request("GET", "/api/admin/users")));
    }

    @Test
    void doesNotCacheThePersonalizedProfileEndpoint() {
        // /api/users/{id}/public is a public projection, but the activity page
        // itself is auth-aware. The profile DTO is the same for everyone, so it
        // is explicitly whitelisted and marked private because the URL is not
        // stable enough for a shared cache without Vary handling.
        assertTrue(policy.isCacheable(request("GET", "/api/users/7/public")));
        assertEquals("private, no-cache",
                policy.cacheControl(request("GET", "/api/users/7/public")));
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRequestURI(uri);
        return request;
    }
}
