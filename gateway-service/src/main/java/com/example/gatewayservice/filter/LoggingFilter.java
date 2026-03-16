package com.example.gatewayservice.filter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;

@Component
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_ID = "X-Request-Id";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String SPAN_ID_HEADER = "X-Span-Id";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Tracer tracer;

    public LoggingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        String requestId = request.getHeaders().getFirst(REQUEST_ID);
        if (requestId == null || requestId.isEmpty()) {
            requestId = generateHexId(16);
        }

        String traceId = request.getHeaders().getFirst(TRACE_ID_HEADER);
        String spanId = request.getHeaders().getFirst(SPAN_ID_HEADER);

        Span span;
        if (traceId != null && !traceId.isEmpty()) {
            span = tracer.spanBuilder(request.getMethod() + " " + request.getPath())
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
            span = tracer.spanBuilder(request.getMethod() + " " + request.getPath()).startSpan();
        }

        String currentTraceId = span.getSpanContext().getTraceId();
        String currentSpanId = span.getSpanContext().getSpanId();

        MDC.put("traceId", currentTraceId);
        MDC.put("spanId", currentSpanId);

        ServerHttpRequest mutatedRequest = request.mutate()
                .header(REQUEST_ID, requestId)
                .header(TRACE_ID_HEADER, currentTraceId)
                .header(SPAN_ID_HEADER, currentSpanId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        final String finalRequestId = requestId;
        final String finalTraceId = currentTraceId;
        final String finalSpanId = currentSpanId;
        final long startTime = System.currentTimeMillis();

        log.info("=".repeat(100));
        log.info("🔥 ВХОДЯЩИЙ ЗАПРОС [{}]", finalRequestId);
        log.info("   Метод: {}", request.getMethod());
        log.info("   Путь: {}", request.getPath());
        log.info("   Remote: {}", request.getRemoteAddress());
        log.info("   TraceId: {}", finalTraceId);
        log.info("   SpanId: {}", finalSpanId);
        log.info("=".repeat(100));

        return chain.filter(mutatedExchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            long duration = System.currentTimeMillis() - startTime;

            log.info("=".repeat(100));
            log.info("✅ ИСХОДЯЩИЙ ОТВЕТ [{}]", finalRequestId);
            log.info("   Статус: {}", response.getStatusCode());
            log.info("   Время: {} ms", duration);
            log.info("   TraceId: {}", finalTraceId);
            log.info("   SpanId: {}", finalSpanId);
            log.info("=".repeat(100));
            
            span.end();
            MDC.remove("traceId");
            MDC.remove("spanId");
        }));
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

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
