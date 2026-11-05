package org.example.helptreeservice.config;

import jakarta.servlet.http.HttpServletRequest;
import org.example.helptreeservice.filter.PublicCacheControlFilter;
import org.example.helptreeservice.security.PublicCachePolicy;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * Conditional GET for the public projections only.
 *
 * <p>{@link ShallowEtagHeaderFilter} buffers the whole response in memory, so it
 * must never run for uploads, streaming, admin listings or any authenticated
 * endpoint. Both filters are therefore registered on {@code /api/*} and the
 * policy rejects everything outside the public whitelist.</p>
 *
 * <p>Order matters: Cache-Control is set first (+20), then the ETag wrapper
 * (+30) copies the buffered headers — including the directives — to the real
 * response, so a 304 keeps them.</p>
 */
@Configuration
public class HttpCacheConfig {

    @Bean
    public FilterRegistrationBean<PublicCacheControlFilter> publicCacheControlFilterRegistration(
            PublicCachePolicy policy) {
        FilterRegistrationBean<PublicCacheControlFilter> registration =
                new FilterRegistrationBean<>(new PublicCacheControlFilter(policy));
        registration.setName("publicCacheControlFilter");
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> publicEtagFilterRegistration(
            PublicCachePolicy policy) {
        ShallowEtagHeaderFilter filter = new ShallowEtagHeaderFilter() {
            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                return !policy.isCacheable(request);
            }
        };
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setName("publicEtagFilter");
        registration.addUrlPatterns("/api/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
        return registration;
    }
}
