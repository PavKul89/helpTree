package com.example.gatewayservice.exception;

import com.example.gatewayservice.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Component
@Order(-2)
public class GlobalExceptionHandler implements WebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        String path = exchange.getRequest().getPath().toString();

        if (path.startsWith("/actuator") || path.startsWith("/error")) {
            return Mono.error(ex);
        }

        HttpStatus status;
        String message;

        if (ex instanceof ResponseStatusException responseStatusException) {
            status = HttpStatus.valueOf(responseStatusException.getStatusCode().value());
            message = responseStatusException.getReason();
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "Внутренняя ошибка сервера";
        }

        log.error("Gateway error: status={}, message={}, path={}", status.value(), message, path, ex);

        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.setStatusCode(status);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .status(status.value())
                .error(status.value() == 429 ? "Too Many Requests" : status.getReasonPhrase())
                .message(message != null ? message : "Внутренняя ошибка сервера")
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();

        return response.writeWith(Mono.just(response.bufferFactory().wrap(
                toJson(errorResponse).getBytes()
        )));
    }

    private String toJson(ErrorResponse error) {
        return String.format("""
                {
                    "status": %d,
                    "error": "%s",
                    "message": "%s",
                    "path": "%s",
                    "timestamp": "%s"
                }
                """,
                error.getStatus(),
                error.getError(),
                error.getMessage(),
                error.getPath(),
                error.getTimestamp()
        );
    }
}
