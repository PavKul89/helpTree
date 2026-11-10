package org.example.helptreeservice.security;

import org.example.helptreeservice.config.AuthConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientInfoResolverTest {

    @Test
    void ignoresForwardedHeadersWhenTrustIsDisabled() {
        AuthConfig config = new AuthConfig();
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "203.0.113.10");
        request.addHeader("X-Real-IP", "203.0.113.11");

        assertEquals("192.0.2.1", new ClientInfoResolver(config).resolveIp(request));
    }

    @Test
    void usesFirstForwardedAddressWhenTrustIsEnabled() {
        AuthConfig config = trustedConfig();
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "203.0.113.10, 198.51.100.4");
        request.addHeader("X-Real-IP", "203.0.113.11");

        assertEquals("203.0.113.10", new ClientInfoResolver(config).resolveIp(request));
    }

    @Test
    void fallsBackToRealIpWhenForwardedForIsEmpty() {
        AuthConfig config = trustedConfig();
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", " ");
        request.addHeader("X-Real-IP", "2001:db8::10");

        assertEquals("2001:db8::10", new ClientInfoResolver(config).resolveIp(request));
    }

    @Test
    void fallsBackToRemoteAddressWhenForwardedAddressIsInvalid() {
        AuthConfig config = trustedConfig();
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "not-an-ip, 203.0.113.10");
        request.addHeader("X-Real-IP", "203.0.113.11");

        assertEquals("192.0.2.1", new ClientInfoResolver(config).resolveIp(request));
    }

    @Test
    void fallsBackToRemoteAddressWhenRealIpIsInvalid() {
        AuthConfig config = trustedConfig();
        MockHttpServletRequest request = request();
        request.addHeader("X-Real-IP", "999.1.1.1");

        assertEquals("192.0.2.1", new ClientInfoResolver(config).resolveIp(request));
    }

    private AuthConfig trustedConfig() {
        AuthConfig config = new AuthConfig();
        config.setTrustForwardedHeaders(true);
        return config;
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.0.2.1");
        return request;
    }
}
