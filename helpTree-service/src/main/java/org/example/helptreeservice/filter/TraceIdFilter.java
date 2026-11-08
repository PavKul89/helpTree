package org.example.helptreeservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.helptreeservice.util.LogContext;
import org.example.helptreeservice.util.TraceIds;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Assigns a correlation id before security filters run, mirrors it into MDC for
 * logging and returns it in the {@code X-Request-Id} response header. Error
 * responses use the same value in their {@code traceId} field, so a user can
 * quote one id to support and it can be found in the logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String traceId = TraceIds.resolveOrGenerate(request.getHeader(TraceIds.HEADER));
        request.setAttribute(TraceIds.REQUEST_ATTRIBUTE, traceId);
        response.setHeader(TraceIds.HEADER, traceId);
        MDC.put(TraceIds.MDC_KEY, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceIds.MDC_KEY);
            // Never leak a previous request's actor/error into a pooled thread.
            LogContext.clear();
        }
    }
}
