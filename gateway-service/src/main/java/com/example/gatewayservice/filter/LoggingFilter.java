package com.example.gatewayservice.filter;

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
@Slf4j
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final String REQUEST_ID = "X-Request-Id";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String SPAN_ID_HEADER = "X-Span-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        String requestId = request.getHeaders().getFirst(REQUEST_ID);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }

        String traceId = request.getHeaders().getFirst(TRACE_ID_HEADER);
        String spanId = request.getHeaders().getFirst(SPAN_ID_HEADER);

        if (traceId == null) {
            traceId = UUID.randomUUID().toString();
        }
        if (spanId == null) {
            spanId = UUID.randomUUID().toString();
        }

        MDC.put("traceId", traceId);
        MDC.put("spanId", spanId);

        ServerHttpRequest mutatedRequest = request.mutate()
                .header(REQUEST_ID, requestId)
                .header(TRACE_ID_HEADER, traceId)
                .header(SPAN_ID_HEADER, spanId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        final String finalRequestId = requestId;
        final String finalTraceId = traceId;
        final String finalSpanId = spanId;
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
            
            MDC.remove("traceId");
            MDC.remove("spanId");
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}