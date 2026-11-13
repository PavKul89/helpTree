package org.example.helptreeservice.security;

import org.example.helptreeservice.filter.RateLimiterFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void prometheusScrapeChainIsDisabledWithoutAPassword() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("app.metrics.prometheus.password", " ");
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);

        assertFalse(new SecurityConfig.PrometheusScrapeCredentialsConfigured()
                .matches(context, mock(AnnotationMetadata.class)));
    }

    @Test
    void prometheusScrapeChainIsEnabledWithAPassword() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("app.metrics.prometheus.password", "local-test-password");
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);

        assertTrue(new SecurityConfig.PrometheusScrapeCredentialsConfigured()
                .matches(context, mock(AnnotationMetadata.class)));
    }
}
