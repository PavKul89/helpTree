package org.example.helptreeservice.filter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
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
import java.security.SecureRandom;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class LoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID = "X-Request-Id";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String SPAN_ID_HEADER = "X-Span-Id";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Tracer tracer;

    public LoggingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID);
        if (requestId == null || requestId.isEmpty()) {
            requestId = generateHexId(16);
        }

        String traceId = request.getHeader(TRACE_ID_HEADER);
        String spanId = request.getHeader(SPAN_ID_HEADER);

        Span span;
        if (traceId != null && !traceId.isEmpty()) {
            span = tracer.spanBuilder("HTTP " + request.getMethod() + " " + request.getRequestURI())
                    .setParent(io.opentelemetry.context.Context.current().with(
                            io.opentelemetry.api.trace.Span.wrap(
                                    io.opentelemetry.api.trace.SpanContext.create(
                                            traceId,
                                            spanId != null ? spanId : generateHexId(8),
                                            io.opentelemetry.api.trace.TraceFlags.getDefault(),
                                            io.opentelemetry.api.trace.TraceState.getDefault()
                                    )
                            )
                    ))
                    .startSpan();
        } else {
            span = tracer.spanBuilder("HTTP " + request.getMethod() + " " + request.getRequestURI()).startSpan();
        }

        String currentTraceId = span.getSpanContext().getTraceId();
        String currentSpanId = span.getSpanContext().getSpanId();

        MDC.put("traceId", currentTraceId);
        MDC.put("spanId", currentSpanId);

        long startTime = System.currentTimeMillis();
        response.setHeader(REQUEST_ID, requestId);
        response.setHeader(TRACE_ID_HEADER, currentTraceId);
        response.setHeader(SPAN_ID_HEADER, currentSpanId);

        try {
            log.info("=".repeat(100));
            log.info("🔥 ВХОДЯЩИЙ ЗАПРОС [{}]", requestId);
            log.info("   Метод: {}", request.getMethod());
            log.info("   Путь: {}", request.getRequestURI());
            log.info("   Remote: {}", request.getRemoteAddr());
            log.info("   TraceId: {}", currentTraceId);
            log.info("   SpanId: {}", currentSpanId);
            log.info("=".repeat(100));

            filterChain.doFilter(request, response);

            long duration = System.currentTimeMillis() - startTime;
            log.info("=".repeat(100));
            log.info("✅ ИСХОДЯЩИЙ ОТВЕТ [{}]", requestId);
            log.info("   Статус: {}", response.getStatus());
            log.info("   Время: {} ms", duration);
            log.info("   TraceId: {}", currentTraceId);
            log.info("   SpanId: {}", currentSpanId);
            log.info("=".repeat(100));
        } finally {
            span.end();
            MDC.remove("traceId");
            MDC.remove("spanId");
        }
    }

    private String generateHexId(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        StringBuilder sb = new StringBuilder(bytes * 2);
        for (byte b : buffer) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}