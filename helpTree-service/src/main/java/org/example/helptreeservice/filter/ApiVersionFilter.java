package org.example.helptreeservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Advertises the current public API version on every {@code /api} response.
 *
 * <p>Versioning policy: additive changes (new fields, endpoints, error codes)
 * stay in the current version; breaking changes move to {@code /api/v2} and the
 * old version is kept for a documented deprecation window. See
 * {@code docs/API_CONTRACT.md}.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiVersionFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Version";

    private final String apiVersion;

    public ApiVersionFilter(@Value("${app.api.version:1}") String apiVersion) {
        this.apiVersion = apiVersion;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI() != null && request.getRequestURI().startsWith("/api/")) {
            response.setHeader(HEADER, apiVersion);
        }
        filterChain.doFilter(request, response);
    }
}
