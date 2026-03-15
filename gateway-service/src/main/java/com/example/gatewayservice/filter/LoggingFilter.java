package com.example.gatewayservice.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class LoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingFilter.class);
    private static final String REQUEST_ID = "X-Request-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = request.getHeaders().getFirst(REQUEST_ID);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString();
        }

        final String reqId = requestId;
        long startTime = System.currentTimeMillis();

        // ДОБАВЛЯЕМ Request ID в заголовки для микросервисов
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(REQUEST_ID, reqId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        log.info("=".repeat(100));
        log.info("🔥 ВХОДЯЩИЙ ЗАПРОС [{}]", reqId);
        log.info("   Метод: {}", request.getMethod());
        log.info("   Путь: {}", request.getPath());
        log.info("   Remote: {}", request.getRemoteAddress());
        log.info("   Headers: {}", request.getHeaders());
        log.info("=".repeat(100));

        return chain.filter(mutatedExchange).then(Mono.fromRunnable(() -> {
            ServerHttpResponse response = exchange.getResponse();
            long duration = System.currentTimeMillis() - startTime;

            log.info("=".repeat(100));
            log.info("✅ ИСХОДЯЩИЙ ОТВЕТ [{}]", reqId);
            log.info("   Статус: {}", response.getStatusCode());
            log.info("   Путь: {}", request.getPath());
            log.info("   Время: {} ms", duration);
            log.info("   Headers: {}", response.getHeaders());
            log.info("=".repeat(100));
        }));
    }

    @Override
    public int getOrder() {
        return -1;  // Выполняется до других фильтров
    }
}