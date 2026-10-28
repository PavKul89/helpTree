package org.example.helptreeservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    @Test
    void jwtFilterIsRegisteredOnlyInsideSpringSecurityChain() {
        SecurityConfig securityConfig = new SecurityConfig(mock(JwtAuthFilter.class));

        FilterRegistrationBean<JwtAuthFilter> registration =
                securityConfig.jwtAuthFilterRegistration();

        assertFalse(registration.isEnabled());
    }
}
