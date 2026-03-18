package com.example.gatewayservice.filter;

import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.TraceContext;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private final Tracer tracer;

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

        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    HttpStatusCode statusCode = exchange.getResponse().getStatusCode();
                    int status = statusCode != null ? statusCode.value() : 200;
                    Duration duration = Duration.between(start, Instant.now());
                    String timeEnd = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").format(LocalDateTime.now());

                    // Пробуем получить span разными способами
                    String traceId = getTraceId();
                    String spanId = getSpanId();

                    log.info("""
                            
                            ╔════════════════════════════════════════════════════════════════════════════╗
                            ║ 📊 ИНФОРМАЦИЯ О ЗАПРОСЕ                                                    ║
                            ╠════════════════════════════════════════════════════════════════════════════╣
                            ║ 🕐 Время:      {} → {}                             
                            ║ 📍 Method:     {}                                 
                            ║ 🎯 Path:       {}                         
                            ║ 🌐 Client IP:  {}                 
                            ║ 📱 User-Agent: {}   
                            ║ 🔗 RequestID:  {}  
                            ║ 🔍 TraceID:    {}  
                            ║ 🔗 SpanID:     {}  
                            ║ ⏱️ Длит.:      {} ms                                
                            ║ 📊 Status:     {}                                     
                            ╚════════════════════════════════════════════════════════════════════════════╝
                            """,
                            timeStart, timeEnd, method, path, clientIp,
                            truncate(userAgent, 30), requestId, traceId, spanId,
                            duration.toMillis(), status);
                })
                .doOnError(error -> {
                    Duration duration = Duration.between(start, Instant.now());

                    String traceId = getTraceId();
                    String spanId = getSpanId();

                    log.error("""
                            
                            ╔════════════════════════════════════════════════════════════════════════════╗
                            ║ ❌ ОШИБКА ЗАПРОСА                                                          ║
                            ╠════════════════════════════════════════════════════════════════════════════╣
                            ║ 🕐 Время:      {}                                                    
                            ║ 📍 Method:     {}                                 
                            ║ 🎯 Path:       {}                         
                            ║ 🔍 TraceID:    {}  
                            ║ 🔗 SpanID:     {}  
                            ║ ⏱️ Длит.:      {} ms                                                
                            ║ 💥 Error:      {}    
                            ║ 🔗 RequestID:  {}  
                            ╚════════════════════════════════════════════════════════════════════════════╝
                            """,
                            timeStart, method, path, traceId, spanId,
                            duration.toMillis(), error.getMessage(), requestId, error);
                });
    }

    private String getTraceId() {
        try {
            if (tracer == null) return "TRACER-NULL";
            TraceContext context = tracer.currentSpan() != null
                    ? tracer.currentSpan().context()
                    : null;
            if (context != null) {
                return context.traceId();
            }

            // Пробуем получить из текущего контекста
            var span = tracer.currentSpan();
            if (span != null) {
                return span.context().traceId();
            }
        } catch (Exception e) {
            log.debug("Error getting traceId: {}", e.getMessage());
        }
        return "NO-TRACE";
    }

    private String getSpanId() {
        try {
            if (tracer == null) return "TRACER-NULL";
            TraceContext context = tracer.currentSpan() != null
                    ? tracer.currentSpan().context()
                    : null;
            if (context != null) {
                return context.spanId();
            }

            // Пробуем получить из текущего контекста
            var span = tracer.currentSpan();
            if (span != null) {
                return span.context().spanId();
            }
        } catch (Exception e) {
            log.debug("Error getting spanId: {}", e.getMessage());
        }
        return "NO-SPAN";
    }

    private String truncate(String str, int length) {
        if (str == null) return "unknown";
        return str.length() <= length ? str : str.substring(0, length) + "...";
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;  // Выполняемся после создания span
    }
}