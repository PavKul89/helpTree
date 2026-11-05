package org.example.helptreeservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.helptreeservice.security.PublicCachePolicy;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Adds {@code Cache-Control}/{@code Vary} before the ETag filter computes the
 * body hash, so a 304 response carries the same cache directives as a 200.
 */
public class PublicCacheControlFilter extends OncePerRequestFilter {

    private final PublicCachePolicy policy;

    public PublicCacheControlFilter(PublicCachePolicy policy) {
        this.policy = policy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        if (policy.isCacheable(request)) {
            response.setHeader("Cache-Control", policy.cacheControl(request));
            if (policy.isAuthAware(request)) {
                // A shared cache must never reuse an owner's response.
                response.addHeader("Vary", "Authorization");
                response.addHeader("Vary", "Cookie");
            }
        }
        filterChain.doFilter(request, response);
    }
}
