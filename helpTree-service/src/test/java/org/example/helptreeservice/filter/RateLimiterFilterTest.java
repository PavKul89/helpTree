package org.example.helptreeservice.filter;

import jakarta.servlet.FilterChain;
import org.example.helptreeservice.config.RateLimitConfig;
import org.example.helptreeservice.security.ClientInfoResolver;
import org.example.helptreeservice.service.ratelimit.DistributedRateLimiter;
import org.example.helptreeservice.service.ratelimit.RateLimitDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RateLimiterFilterTest {

    private RateLimitConfig config;
    private DistributedRateLimiter rateLimiter;
    private ClientInfoResolver clientInfoResolver;
    private RateLimiterFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        config = new RateLimitConfig();
        config.setEnabled(true);
        rateLimiter = mock(DistributedRateLimiter.class);
        clientInfoResolver = mock(ClientInfoResolver.class);
        filter = new RateLimiterFilter(config, rateLimiter, clientInfoResolver);
        chain = mock(FilterChain.class);
        when(clientInfoResolver.resolveIp(any())).thenReturn("203.0.113.7");
    }

    @Test
    void shouldRejectWithFast429AndRetryAfter() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/auth/login", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.acquire(anyString(), eq(10), any(Duration.class)))
                .thenReturn(RateLimitDecision.rejected(10, 37));

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertEquals("37", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("Too Many Requests"));
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void loginKeyShouldContainRouteIpAndAnonymousIdentity() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/auth/login", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.acquire(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(RateLimitDecision.allowed(10, 9, 60));

        filter.doFilter(request, response, chain);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter).acquire(key.capture(), eq(10), any(Duration.class));
        assertEquals("rl:auth.login:203.0.113.7:anon", key.getValue());
        verify(chain).doFilter(request, response);
    }

    @Test
    void authenticatedUsersBehindOneIpShouldUseDifferentKeys() throws Exception {
        MockHttpServletRequest first = request("POST", "/api/posts/123", "42");
        MockHttpServletRequest second = request("POST", "/api/posts/123", "43");

        when(rateLimiter.acquire(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(RateLimitDecision.allowed(60, 59, 60));

        filter.doFilter(first, new MockHttpServletResponse(), chain);
        filter.doFilter(second, new MockHttpServletResponse(), chain);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(rateLimiter, org.mockito.Mockito.times(2))
                .acquire(keys.capture(), eq(60), any(Duration.class));
        List<String> values = keys.getAllValues();
        assertEquals("rl:write:/api/posts/{id}:203.0.113.7:u42", values.get(0));
        assertEquals("rl:write:/api/posts/{id}:203.0.113.7:u43", values.get(1));
    }

    @Test
    void shouldSkipPreflightActuatorAndWebSocketHandshake() throws Exception {
        MockHttpServletRequest options = request("OPTIONS", "/api/posts", null);
        MockHttpServletRequest actuator = request("GET", "/actuator/health", null);
        MockHttpServletRequest ws = request("GET", "/ws/info", null);

        filter.doFilter(options, new MockHttpServletResponse(), chain);
        filter.doFilter(actuator, new MockHttpServletResponse(), chain);
        filter.doFilter(ws, new MockHttpServletResponse(), chain);

        verifyNoInteractions(rateLimiter);
    }

    @Test
    void disabledLimiterShouldDoNothing() throws Exception {
        config.setEnabled(false);

        filter.doFilter(request("POST", "/api/posts", null), new MockHttpServletResponse(), chain);

        verifyNoInteractions(rateLimiter);
        verify(chain).doFilter(any(), any());
    }

    @Test
    void storeFailureShouldFailOpen() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/posts", null);
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(rateLimiter.acquire(anyString(), anyInt(), any(Duration.class)))
                .thenThrow(new IllegalStateException("db down"));

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals(200, response.getStatus());
    }

    private MockHttpServletRequest request(String method, String path, String userId) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (userId != null) {
            request.setAttribute("X-User-Id", userId);
        }
        return request;
    }
}
