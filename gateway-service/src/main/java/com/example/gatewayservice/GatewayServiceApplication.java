package com.example.gatewayservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Slf4j
@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
        log.info("🚀 Api-gateway service started successfully at: http://localhost:8081");
        log.info("🚀 HelpTree service started successfully at: http://localhost:8081");
        log.info("🚀 Kafka-ui successfully at: http://localhost:8082");
        log.info("🚀 Prometheus successfully at: http://localhost:9090");
        log.info("🚀 Grafana successfully at: http://localhost:3000");
        log.info("🚀 Jaeger successfully at: http://localhost:16686");
        log.info("🚀 MinIO successfully at: http://localhost:9001/browser/helptree");
    }

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:3001", "http://localhost:3002", "http://localhost:5173"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("helpTree-service", r -> r
                        .path(
                                "/api/auth/**",
                                "/api/users/**",
                                "/api/posts/**",
                                "/api/helps/**",
                                "/api/images/**",
                                "/api/test/**",
                                "/api/reviews/**",
                                "/api/chats/**",
                                "/api/comments/**",
                                "/api/achievements/**",
                                "/api/helps/stats",
                                "/api/activities/**",
                                "/api/stats/**",
                                "/api/ratings/**"
                        )
                        .filters(f -> f
                                .addRequestHeader("X-Forwarded-For", "gateway")
                                .addRequestHeader("Authorization", "")
                                .addRequestHeader("X-User-Id", "")
                                .addRequestHeader("X-User-Role", "")
                                .addRequestHeader("X-User-Email", "")
                                .preserveHostHeader()
                                .circuitBreaker(config -> config
                                        .setName("usersService")))
                        .uri("http://localhost:8081"))

                .route("helpTree-internal", r -> r
                        .path("/internal/users/**")
                        .filters(f -> f
                                .addRequestHeader("X-Forwarded-For", "gateway"))
                        .uri("http://localhost:8081"))
                .build();
    }
}
