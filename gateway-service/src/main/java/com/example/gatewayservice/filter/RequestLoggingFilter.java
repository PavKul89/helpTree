package com.example.gatewayservice.filter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private final Tracer tracer;
    private final MeterRegistry meterRegistry;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();
        String method = request.getMethod().toString();
        String clientIp = Optional.ofNullable(request.getRemoteAddress())
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("unknown");
        String userAgent = request.getHeaders().getFirst("User-Agent");
        String traceId = getCurrentTraceId();

        // Замеряем время выполнения
        Instant start = Instant.now();

        // Создаем метрики
        Timer.Sample timerSample = Timer.start(meterRegistry);
        AtomicReference<Integer> statusCodeRef = new AtomicReference<>(0);

        // Детальное логирование заголовков трассировки
        logTracesHeaders(request);

        // Логируем начало запроса
        log.info(">>> [{}] {} - Запрос от клиента: {}, User-Agent: {}, TraceId: {}",
                method, path, clientIp, userAgent, traceId);

        // Логируем важные заголовки
        logHeaders(request);

        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                    int status = statusCode != null ? statusCode.value() : 200;
                    statusCodeRef.set(status);

                    Duration duration = Duration.between(start, Instant.now());
                    timerSample.stop(meterRegistry.timer("gateway.request.duration",
                            "method", method,
                            "path", path,
                            "status", String.valueOf(status),
                            "traceId", traceId));

                    // Успешное завершение
                    log.info("<<< [{}] {} - Статус: {}, Время: {}ms, TraceId: {}",
                            method, path, status, duration.toMillis(), traceId);
                })
                .doOnError(error -> {
                    Duration duration = Duration.between(start, Instant.now());
                    timerSample.stop(meterRegistry.timer("gateway.request.errors",
                            "method", method,
                            "path", path,
                            "error", error.getClass().getSimpleName(),
                            "traceId", traceId));

                    // Ошибка
                    log.error("!!! [{}] {} - Ошибка: {}, Время: {}ms, TraceId: {}",
                            method, path, error.getMessage(), duration.toMillis(), traceId, error);
                });
    }

    private String getCurrentTraceId() {
        try {
            if (tracer != null && tracer.currentSpan() != null) {
                return tracer.currentSpan().context().traceId();
            }
        } catch (Exception e) {
            log.debug("Failed to get trace ID", e);
        }
        return "N/A";
    }

    private void logTracesHeaders(ServerHttpRequest request) {
        String traceParent = request.getHeaders().getFirst("traceparent");
        String traceId = request.getHeaders().getFirst("X-Trace-Id");
        String b3TraceId = request.getHeaders().getFirst("X-B3-TraceId");

        if (traceParent != null) {
            log.debug("Trace Context - traceparent: {}", traceParent);
        }
        if (traceId != null) {
            log.debug("Trace Context - X-Trace-Id: {}", traceId);
        }
        if (b3TraceId != null) {
            log.debug("Trace Context - X-B3-TraceId: {}", b3TraceId);
        }
    }

    private void logHeaders(ServerHttpRequest request) {
        // Логируем только важные заголовки
        String[] importantHeaders = {
                "Content-Type", "Content-Length", "Authorization",
                "X-Forwarded-For", "X-Request-ID"
        };

        for (String header : importantHeaders) {
            String value = request.getHeaders().getFirst(header);
            if (value != null) {
                if (header.equals("Authorization") && value.startsWith("Bearer")) {
                    log.debug("Header {}: Bearer [PROTECTED]", header);
                } else {
                    log.debug("Header {}: {}", header, value);
                }
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}