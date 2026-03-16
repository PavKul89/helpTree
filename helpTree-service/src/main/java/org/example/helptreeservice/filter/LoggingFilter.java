package org.example.helptreeservice.filter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
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

    public LoggingFilter() {
        this.tracer = io.opentelemetry.api.GlobalOpenTelemetry.getTracer("helpTree-service");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = request.getHeader(REQUEST_ID);
        if (requestId == null || requestId.isEmpty()) {
            requestId = generateHexId(16);
        }

        String incomingTraceId = request.getHeader(TRACE_ID_HEADER);
        String incomingParentSpanId = request.getHeader(SPAN_ID_HEADER);

        // СОЗДАЕМ НОВЫЙ SPAN ДЛЯ HELPTREE-СЕРВИСА с УНИКАЛЬНЫМ spanId
        Span helpTreeSpan;
        Context parentContext = Context.current();

        if (incomingTraceId != null && !incomingTraceId.isEmpty() &&
                incomingParentSpanId != null && !incomingParentSpanId.isEmpty()) {

            // Создаем родительский контекст из входящих заголовков
            io.opentelemetry.api.trace.SpanContext parentSpanContext =
                    io.opentelemetry.api.trace.SpanContext.createFromRemoteParent(
                            incomingTraceId,
                            incomingParentSpanId,
                            io.opentelemetry.api.trace.TraceFlags.getSampled(),
                            io.opentelemetry.api.trace.TraceState.getDefault()
                    );

            // ВАЖНО: Здесь мы создаем дочерний span, который получит НОВЫЙ spanId
            helpTreeSpan = tracer.spanBuilder("helptree: " + request.getMethod() + " " + request.getRequestURI())
                    .setParent(Context.current().with(io.opentelemetry.api.trace.Span.wrap(parentSpanContext)))
                    .setSpanKind(io.opentelemetry.api.trace.SpanKind.SERVER)
                    .startSpan();
        } else {
            helpTreeSpan = tracer.spanBuilder("helptree: " + request.getMethod() + " " + request.getRequestURI())
                    .setSpanKind(io.opentelemetry.api.trace.SpanKind.SERVER)
                    .startSpan();
        }

        // Устанавливаем span как текущий
        Scope scope = helpTreeSpan.makeCurrent();

        String currentTraceId = helpTreeSpan.getSpanContext().getTraceId();
        String currentSpanId = helpTreeSpan.getSpanContext().getSpanId();

        MDC.put("traceId", currentTraceId);
        MDC.put("spanId", currentSpanId);

        // В ответе отправляем НОВЫЙ spanId, который создал helpTree
        response.setHeader(REQUEST_ID, requestId);
        response.setHeader(TRACE_ID_HEADER, currentTraceId);
        response.setHeader(SPAN_ID_HEADER, currentSpanId);

        final String finalRequestId = requestId;
        final long startTime = System.currentTimeMillis();

        log.info("=".repeat(100));
        log.info("🔥 HELPTREE: ВХОДЯЩИЙ ЗАПРОС [{}]", finalRequestId);
        log.info("   Метод: {}", request.getMethod());
        log.info("   Путь: {}", request.getRequestURI());
        log.info("   Remote: {}", request.getRemoteAddr());
        log.info("   TraceId: {}", currentTraceId);
        log.info("   SpanId: {}", currentSpanId);  // Это НОВЫЙ spanId
        log.info("   Parent SpanId (из гетевея): {}", incomingParentSpanId);
        log.info("=".repeat(100));

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;

            log.info("=".repeat(100));
            log.info("✅ HELPTREE: ИСХОДЯЩИЙ ОТВЕТ [{}]", finalRequestId);
            log.info("   Статус: {}", response.getStatus());
            log.info("   Время: {} ms", duration);
            log.info("   TraceId: {}", currentTraceId);
            log.info("   SpanId: {}", currentSpanId);
            log.info("=".repeat(100));

            // Завершаем span
            scope.close();
            helpTreeSpan.end();

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