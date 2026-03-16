package com.example.gatewayservice.filter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
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

        // Получаем заголовки трассировки от клиента
        String incomingTraceId = request.getHeaders().getFirst(TRACE_ID_HEADER);
        String incomingSpanId = request.getHeaders().getFirst(SPAN_ID_HEADER);

        // СОЗДАЕМ НОВЫЙ SPAN ДЛЯ ГЕТЕВЕЯ
        Span gatewaySpan;
        Context parentContext = Context.current();

        if (incomingTraceId != null && !incomingTraceId.isEmpty() && incomingSpanId != null && !incomingSpanId.isEmpty()) {
            io.opentelemetry.api.trace.SpanContext parentSpanContext =
                    io.opentelemetry.api.trace.SpanContext.createFromRemoteParent(
                            incomingTraceId,
                            incomingSpanId,
                            io.opentelemetry.api.trace.TraceFlags.getSampled(),
                            io.opentelemetry.api.trace.TraceState.getDefault()
                    );
            parentContext = parentContext.with(io.opentelemetry.api.trace.Span.wrap(parentSpanContext));

            gatewaySpan = tracer.spanBuilder("gateway: " + request.getMethod() + " " + request.getPath())
                    .setParent(parentContext)
                    .setSpanKind(io.opentelemetry.api.trace.SpanKind.SERVER)
                    .startSpan();

            log.info("📋 Получен входящий traceId от клиента: {}", incomingTraceId);
        } else {
            gatewaySpan = tracer.spanBuilder("gateway: " + request.getMethod() + " " + request.getPath())
                    .setSpanKind(io.opentelemetry.api.trace.SpanKind.SERVER)
                    .startSpan();

            log.info("🆕 Создан новый traceId: {}", gatewaySpan.getSpanContext().getTraceId());
        }

        // Устанавливаем gateway span как текущий
        Scope gatewayScope = gatewaySpan.makeCurrent();

        String currentTraceId = gatewaySpan.getSpanContext().getTraceId();
        String currentSpanId = gatewaySpan.getSpanContext().getSpanId();

        MDC.put("traceId", currentTraceId);
        MDC.put("spanId", currentSpanId);

        // СОЗДАЕМ НОВЫЙ SPAN ДЛЯ ВЫЗОВА СЛЕДУЮЩЕГО СЕРВИСА
        Span downstreamSpan = tracer.spanBuilder("downstream: " + request.getMethod() + " " + request.getPath())
                .setParent(Context.current().with(gatewaySpan))
                .setSpanKind(io.opentelemetry.api.trace.SpanKind.CLIENT)
                .startSpan();

        String downstreamTraceId = downstreamSpan.getSpanContext().getTraceId();
        String downstreamSpanId = downstreamSpan.getSpanContext().getSpanId();

        // КРАСИВОЕ ФОРМАТИРОВАНИЕ С TRACE ID
        log.info("=".repeat(100));
        log.info("🔍 TRACE ID: {}", currentTraceId);
        log.info("=".repeat(100));
        log.info("🔥 ГЕТЕВЕЙ: ВХОДЯЩИЙ ЗАПРОС [{}]", requestId);
        log.info("   Метод: {}", request.getMethod());
        log.info("   Путь: {}", request.getPath());
        log.info("   Remote: {}", request.getRemoteAddress());
        log.info("   📌 Gateway Span:");
        log.info("      - TraceId: {}", currentTraceId);
        log.info("      - SpanId: {}", currentSpanId);
        log.info("   📌 Downstream Span (для helpTree):");
        log.info("      - TraceId: {}", downstreamTraceId);
        log.info("      - SpanId: {}", downstreamSpanId);
        log.info("=".repeat(100));

        // Модифицируем запрос, добавляя заголовки с downstream span
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(REQUEST_ID, requestId)
                .header(TRACE_ID_HEADER, downstreamTraceId)
                .header(SPAN_ID_HEADER, downstreamSpanId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        final String finalRequestId = requestId;
        final long startTime = System.currentTimeMillis();

        return chain.filter(mutatedExchange)
                .doFinally(signalType -> {
                    long duration = System.currentTimeMillis() - startTime;

                    log.info("=".repeat(100));
                    log.info("🔍 TRACE ID: {}", currentTraceId);
                    log.info("=".repeat(100));
                    log.info("✅ ГЕТЕВЕЙ: ОТВЕТ [{}]", finalRequestId);
                    log.info("   Статус: {}", exchange.getResponse().getStatusCode());
                    log.info("   Время: {} ms", duration);
                    log.info("   📌 Downstream Span завершен:");
                    log.info("      - SpanId: {}", downstreamSpanId);
                    log.info("   📌 Gateway Span завершен:");
                    log.info("      - SpanId: {}", currentSpanId);
                    log.info("=".repeat(100));

                    // Завершаем spans в правильном порядке
                    downstreamSpan.end();
                    gatewayScope.close();
                    gatewaySpan.end();

                    MDC.remove("traceId");
                    MDC.remove("spanId");
                });
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