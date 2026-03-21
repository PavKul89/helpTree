package com.example.gatewayservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;

@Slf4j
@SpringBootApplication
@EnableScheduling
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
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Сервис пользователей и аутентификации (helpTree-service)
                .route("helpTree-service", r -> r
                        .path(
                                "/api/auth/**",
                                "/api/users/**",
                                "/api/posts/**",
                                "/api/helps/**",
                                "/api/images/**",
                                "/api/test/**"
                        )
                        .filters(f -> f
                                .addRequestHeader("X-Forwarded-For", "gateway")
                                .addRequestHeader("Authorization", "") // placeholder - actual header from client
                                .addRequestHeader("X-User-Id", "")     // placeholder
                                .addRequestHeader("X-User-Role", "")   // placeholder
                                .addRequestHeader("X-User-Email", "") // placeholder
                                .preserveHostHeader()
                                .circuitBreaker(config -> config
                                        .setName("usersService")))
                        .uri("http://localhost:8081"))

                // Сервис рейтингов (rating-service)
                .route("rating-service", r -> r
                        .path("/api/ratings/**")
                        .filters(f -> f
                                .addRequestHeader("X-Forwarded-For", "gateway")
                                .circuitBreaker(config -> config
                                        .setName("ratingService")))
                        .uri("http://localhost:8085"))
                .build();
    }
}
