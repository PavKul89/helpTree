package org.example.helptreeservice.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();
        String clientIp = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        Instant start = Instant.now();

        try {
            filterChain.doFilter(request, response);
        } finally {
            Duration duration = Duration.between(start, Instant.now());
            int status = response.getStatus();

            // MDC уже содержит traceId и spanId от micrometer-tracing
            log.info("""
                    
                    ╔════════════════════════════════════════════════════════════════════════════╗
                    ║ 📊 ИНФОРМАЦИЯ О ЗАПРОСЕ (helpTree)                                         ║
                    ╠════════════════════════════════════════════════════════════════════════════╣
                    ║ 🕐 Время:      {} ms                                                  
                    ║ 📍 Method:     {}                                 
                    ║ 🎯 Path:       {}                         
                    ║ 🌐 Client IP:  {}                 
                    ║ 📱 User-Agent: {}   
                    ║ 🔍 TraceID:    {}  
                    ║ 🔗 SpanID:     {}  
                    ║ 📊 Status:     {}                                     
                    ╚════════════════════════════════════════════════════════════════════════════╝
                    """,
                    duration.toMillis(), method, path, clientIp,
                    truncate(userAgent, 30),
                    MDC.get("traceId"), MDC.get("spanId"),
                    status);
        }
    }

    private String truncate(String str, int length) {
        if (str == null) return "unknown";
        return str.length() <= length ? str : str.substring(0, length) + "...";
    }
}
