package com.example.gatewayservice.filter;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.ScopedSpan;
import lombok.RequiredArgsConstructor;
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

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {

    private final Tracer tracer;
    private static final String REQUEST_ID = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = request.getHeaders().getFirst(REQUEST_ID);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }

        final String finalRequestId = requestId;
        final long startTime = System.currentTimeMillis();

        ServerHttpRequest mutatedRequest = request.mutate()
                .header(REQUEST_ID, finalRequestId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        ScopedSpan span = tracer.startScopedSpan("gateway-request");
        String traceId = span.context().traceId();
        String spanId = span.context().spanId();
        MDC.put("traceId", traceId);
        MDC.put("spanId", spanId);

        final String finalTraceId = traceId;
        final String finalSpanId = spanId;

        try {
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
            }));
        } finally {
            span.end();
            MDC.remove("traceId");
            MDC.remove("spanId");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}