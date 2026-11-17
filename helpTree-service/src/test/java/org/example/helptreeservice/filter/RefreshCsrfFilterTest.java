package org.example.helptreeservice.filter;

import jakarta.servlet.http.Cookie;
import org.example.helptreeservice.config.AuthConfig;
import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.service.AuthCookieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class RefreshCsrfFilterTest {

    private static final String CSRF = "csrf-token";

    private final AuthConfig authConfig = new AuthConfig();
    private AuthCookieService cookies;
    private RefreshCsrfFilter filter;

    @BeforeEach
    void setUp() {
        cookies = new AuthCookieService(authConfig, new EconomyConfig());
        filter = new RefreshCsrfFilter(cookies);
        ReflectionTestUtils.setField(filter, "allowedOrigins",
                "https://helptree.example,https://www.helptree.example");
    }

    @Test
    void otherEndpointsAreNotFiltered() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/posts");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void bodyOnlyRefreshKeepsWorkingForNonBrowserClients() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void cookieRefreshRequiresMatchingCsrfHeader() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(refreshRequest(null, null, null), response, chain);
        assertNull(chain.getRequest());
        assertEquals(403, response.getStatus());

        chain = new MockFilterChain();
        response = new MockHttpServletResponse();
        filter.doFilter(refreshRequest(null, "wrong", null), response, chain);
        assertNull(chain.getRequest());
        assertEquals(403, response.getStatus());
    }

    @Test
    void cookieRefreshWithMatchingCsrfHeaderPasses() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(refreshRequest(null, CSRF, null), new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
    }

    @Test
    void foreignOriginIsRejectedEvenWithMatchingCsrf() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(refreshRequest(null, CSRF, "https://evil.example"), response, chain);

        assertNull(chain.getRequest());
        assertEquals(403, response.getStatus());
    }

    @Test
    void configuredOriginIsAccepted() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(refreshRequest(null, CSRF, "https://www.helptree.example/"),
                new MockHttpServletResponse(), chain);

        assertNotNull(chain.getRequest());
    }

    private MockHttpServletRequest refreshRequest(String refreshToken, String csrfHeader, String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/refresh");
        request.setCookies(
                new Cookie(authConfig.getRefreshCookieName(), refreshToken == null ? "refresh" : refreshToken),
                new Cookie(authConfig.getCsrfCookieName(), CSRF));
        if (csrfHeader != null) {
            request.addHeader(authConfig.getCsrfHeaderName(), csrfHeader);
        }
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        return request;
    }
}
