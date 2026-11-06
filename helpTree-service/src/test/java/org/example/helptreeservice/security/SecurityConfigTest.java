package org.example.helptreeservice.security;

import org.example.helptreeservice.filter.RateLimiterFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    @Test
    void jwtFilterIsRegisteredOnlyInsideSpringSecurityChain() {
        SecurityConfig securityConfig = new SecurityConfig(
                mock(JwtAuthFilter.class), mock(RateLimiterFilter.class));

        FilterRegistrationBean<JwtAuthFilter> registration =
                securityConfig.jwtAuthFilterRegistration();

        assertFalse(registration.isEnabled());
    }

    @Test
    void rateLimiterFilterIsRegisteredOnlyInsideSpringSecurityChain() {
        SecurityConfig securityConfig = new SecurityConfig(
                mock(JwtAuthFilter.class), mock(RateLimiterFilter.class));

        FilterRegistrationBean<RateLimiterFilter> registration =
                securityConfig.rateLimiterFilterRegistration();

        assertFalse(registration.isEnabled());
    }
}
