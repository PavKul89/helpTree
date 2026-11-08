package org.example.helptreeservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.util.TraceIds;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * One structured access-log line per request.
 *
 * <p>The correlation id, user id and error type already live in MDC, so the
 * message only adds the request facts. It deliberately does not log headers,
 * bodies, query strings or the raw user agent: those can contain personal data
 * or credentials and are not needed for operations.</p>
 */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long started = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis = (System.nanoTime() - started) / 1_000_000;
            log.info("http_request method={} path={} status={} durationMs={} clientIp={} requestId={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    durationMillis,
                    request.getRemoteAddr(),
                    MDC.get(TraceIds.MDC_KEY));
        }
    }
}
