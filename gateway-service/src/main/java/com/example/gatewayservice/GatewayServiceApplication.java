package com.example.gatewayservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

@Slf4j
@SpringBootApplication
public class GatewayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayServiceApplication.class, args);
        log.info("🚀 Api-gateway service started successfully at: http://localhost:8081");
        log.info("🚀 HelpTree service started successfully at: http://localhost:8081");
        log.info("🚀 Rating service started successfully at: http://localhost:8085");
        log.info("🚀 Kafka-ui successfully at: http://localhost:8082");
        log.info("🚀 Prometheus successfully at: http://localhost:9090");
        log.info("🚀 Grafana successfully at: http://localhost:3000");
        log.info("🚀 Jaeger successfully at: http://localhost:16686");
        log.info("🚀 MinIO successfully at: http://localhost:9001/browser/helptree");
        log.info("🚀 Notification service at: http://localhost:8087");
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
                                "/api/reviews/**"
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

                .route("rating-service", r -> r
                        .path("/api/ratings/**")
                        .filters(f -> f
                                .addRequestHeader("X-Forwarded-For", "gateway")
                                .circuitBreaker(config -> config
                                        .setName("ratingService")))
                        .uri("http://localhost:8085"))

                .route("helpTree-internal", r -> r
                        .path("/internal/users/**")
                        .filters(f -> f
                                .addRequestHeader("X-Forwarded-For", "gateway"))
                        .uri("http://localhost:8081"))

                .route("rating-internal", r -> r
                        .path("/internal/ratings/**")
                        .filters(f -> f
                                .addRequestHeader("X-Forwarded-For", "gateway"))
                        .uri("http://localhost:8085"))
                .build();
    }
}
