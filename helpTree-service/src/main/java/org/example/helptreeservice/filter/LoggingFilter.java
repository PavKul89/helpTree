package org.example.helptreeservice.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class LoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID = "X-Request-Id";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String SPAN_ID_HEADER = "X-Span-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }

        String traceId = request.getHeader(TRACE_ID_HEADER);
        String spanId = request.getHeader(SPAN_ID_HEADER);

        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }
        
        String parentSpanId = spanId;
        spanId = UUID.randomUUID().toString();

        MDC.put("traceId", traceId);
        MDC.put("spanId", spanId);

        long startTime = System.currentTimeMillis();
        response.setHeader(REQUEST_ID, requestId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        response.setHeader(SPAN_ID_HEADER, spanId);

        try {
            log.info("=".repeat(100));
            log.info("🔥 ВХОДЯЩИЙ ЗАПРОС [{}]", requestId);
            log.info("   Метод: {}", request.getMethod());
            log.info("   Путь: {}", request.getRequestURI());
            log.info("   Remote: {}", request.getRemoteAddr());
            log.info("   TraceId: {}", traceId);
            log.info("   ParentSpanId: {}", parentSpanId != null ? parentSpanId : "N/A");
            log.info("   NewSpanId: {}", spanId);
            log.info("=".repeat(100));

            filterChain.doFilter(request, response);

            long duration = System.currentTimeMillis() - startTime;
            log.info("=".repeat(100));
            log.info("✅ ИСХОДЯЩИЙ ОТВЕТ [{}]", requestId);
            log.info("   Статус: {}", response.getStatus());
            log.info("   Время: {} ms", duration);
            log.info("   TraceId: {}", traceId);
            log.info("   SpanId: {}", spanId);
            log.info("=".repeat(100));
        } finally {
            MDC.remove("traceId");
            MDC.remove("spanId");
        }
    }
}