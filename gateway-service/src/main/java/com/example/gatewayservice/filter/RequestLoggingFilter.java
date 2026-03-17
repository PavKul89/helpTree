package com.example.gatewayservice.filter;

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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().toString();
        String method = request.getMethod().toString();
        String clientIp = Optional.ofNullable(request.getRemoteAddress())
                .map(addr -> addr.getAddress().getHostAddress())
                .orElse("unknown");
        String userAgent = request.getHeaders().getFirst("User-Agent");
        String requestId = request.getId();

        Instant start = Instant.now();
        String timeStart = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").format(LocalDateTime.now());

        log.info("""
                
                ╔════════════════════════════════════════════════════════════════════════════╗
                ║ 🚀 ВХОДЯЩИЙ ЗАПРОС                                                          ║
                ╠════════════════════════════════════════════════════════════════════════════╣
                ║ 🕐 Время:      {}                                                   
                ║ 📍 Method:     {}                                 
                ║ 🎯 Path:       {}                         
                ║ 🌐 Client IP:  {}                 
                ║ 📱 User-Agent: {}   
                ║ 🔗 RequestID:  {}  
                ╚════════════════════════════════════════════════════════════════════════════╝
                """,
                timeStart, method, path, clientIp, truncate(userAgent, 30), requestId);

        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                    int status = statusCode != null ? statusCode.value() : 200;
                    Duration duration = Duration.between(start, Instant.now());
                    String timeEnd = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").format(LocalDateTime.now());

                    // ПОЛУЧАЕМ TRACE ИЗ ЗАГОЛОВКОВ (ОНИ УЖЕ ЕСТЬ!)
                    String traceId = extractTraceId(exchange.getRequest());
                    String spanId = extractSpanId(exchange.getRequest());

                    log.info("""
                            
                            ╔════════════════════════════════════════════════════════════════════════════╗
                            ║ ✅ ОТВЕТ НА ЗАПРОС                                                         ║
                            ╠════════════════════════════════════════════════════════════════════════════╣
                            ║ 🕐 Начало:     {}  →  {}                         
                            ║ ⏱️ Длит.:      {} ms                                
                            ║ 📊 Status:     {}                                     
                            ║ 🔍 TraceID:    {}  
                            ║ 🔗 SpanID:     {}  
                            ║ 🔗 RequestID:  {}  
                            ╚════════════════════════════════════════════════════════════════════════════╝
                            """,
                            timeStart, timeEnd, duration.toMillis(), status,
                            traceId, spanId, requestId);
                })
                .doOnError(error -> {
                    Duration duration = Duration.between(start, Instant.now());
                    String traceId = extractTraceId(exchange.getRequest());
                    String spanId = extractSpanId(exchange.getRequest());

                    log.error("""
                            
                            ╔════════════════════════════════════════════════════════════════════════════╗
                            ║ ❌ ОШИБКА ЗАПРОСА                                                          ║
                            ╠════════════════════════════════════════════════════════════════════════════╣
                            ║ 🕐 Время:      {}                                                    
                            ║ ⏱️ Длит.:      {} ms                                                
                            ║ 🔍 TraceID:    {}  
                            ║ 🔗 SpanID:     {}  
                            ║ 💥 Error:      {}    
                            ║ 🔗 RequestID:  {}  
                            ╚════════════════════════════════════════════════════════════════════════════╝
                            """,
                            timeStart, duration.toMillis(), traceId, spanId,
                            error.getMessage(), requestId, error);
                });
    }

    private String extractTraceId(ServerHttpRequest request) {
        // Из traceparent (W3C формат)
        String traceparent = request.getHeaders().getFirst("traceparent");
        if (traceparent != null && traceparent.startsWith("00-")) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 2) {
                return parts[1]; // traceId
            }
        }
        return "NO-TRACE";
    }

    private String extractSpanId(ServerHttpRequest request) {
        // Из traceparent (W3C формат)
        String traceparent = request.getHeaders().getFirst("traceparent");
        if (traceparent != null && traceparent.startsWith("00-")) {
            String[] parts = traceparent.split("-");
            if (parts.length >= 3) {
                return parts[2]; // spanId
            }
        }
        return "NO-SPAN";
    }

    private String truncate(String str, int length) {
        if (str == null) return "unknown";
        return str.length() <= length ? str : str.substring(0, length) + "...";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}