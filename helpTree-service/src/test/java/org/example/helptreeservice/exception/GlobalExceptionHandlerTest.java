package org.example.helptreeservice.exception;

import org.example.helptreeservice.dto.error.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    @Test
    void notFound_shouldReturn404() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFoundException(
                new NotFoundException("Пост не найден"), request("/api/posts/1"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().getStatus());
        assertEquals("Not Found", response.getBody().getError());
    }

    @Test
    void forbidden_shouldReturn403() {
        ResponseEntity<ErrorResponse> response = handler.handleForbiddenException(
                new ForbiddenException("Чужой ресурс"), request("/api/users/2"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(403, response.getBody().getStatus());
        assertEquals("Forbidden", response.getBody().getError());
    }

    @Test
    void accessDenied_shouldReturn403WithoutInternalDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleAccessDeniedException(
                new AccessDeniedException("Access is denied for user admin with authority ROLE_USER"),
                request("/api/admin"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Forbidden", response.getBody().getError());
        assertEquals("Доступ запрещен", response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("ROLE_USER"));
    }

    @Test
    void unauthorized_shouldReturn401() {
        ResponseEntity<ErrorResponse> response = handler.handleUnauthorizedException(
                new UnauthorizedException("Вы не авторизованы"), request("/api/users/me"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().getStatus());
        assertEquals("Unauthorized", response.getBody().getError());
    }

    @Test
    void conflict_shouldReturn409() {
        ResponseEntity<ErrorResponse> response = handler.handleConflictException(
                new ConflictException("Уже существует"), request("/api/posts"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getStatus());
        assertEquals("Conflict", response.getBody().getError());
    }

    @Test
    void noResourceFound_shouldReturn404() {
        NoResourceFoundException ex = new NoResourceFoundException(
                HttpMethod.GET, "/api/unknown", "No static resource");

        ResponseEntity<ErrorResponse> response = handler.handleNoResourceFound(ex, request("/api/unknown"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("Ресурс не найден", response.getBody().getMessage());
    }

    @Test
    void methodNotSupported_shouldReturn405() {
        HttpRequestMethodNotSupportedException ex =
                new HttpRequestMethodNotSupportedException("PATCH", java.util.List.of("GET"));

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex, request("/api/posts"));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals(405, response.getBody().getStatus());
        assertEquals("Method Not Allowed", response.getBody().getError());
    }

    @Test
    void mediaTypeNotSupported_shouldReturn415() {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException("text/plain");

        ResponseEntity<ErrorResponse> response = handler.handleMediaTypeNotSupported(ex, request("/api/posts"));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals(415, response.getBody().getStatus());
        assertEquals("Unsupported Media Type", response.getBody().getError());
    }

    @Test
    void dataIntegrityViolation_shouldReturn409WithoutSqlDetails() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement; SQL [insert into users ...]; constraint [uk_users_email]");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex, request("/api/users"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("Нарушение целостности данных. Возможно, запись уже существует.",
                response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("SQL"));
        assertFalse(response.getBody().getMessage().contains("uk_users_email"));
    }

    @Test
    void illegalArgument_shouldReturn400WithoutInternalMessage() {
        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(
                new IllegalArgumentException("Password hash algorithm BCrypt failed"), request("/api/auth/login"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Некорректный запрос", response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("BCrypt"));
    }

    @Test
    void unexpectedException_shouldReturn500WithoutStackTraceOrSql() {
        RuntimeException ex = new RuntimeException(
                "org.postgresql.util.PSQLException: ERROR: relation \"users\" does not exist");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex, request("/api/users"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, response.getBody().getStatus());
        assertEquals("Произошла непредвиденная ошибка", response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("PSQLException"));
        assertFalse(response.getBody().getMessage().contains("users"));
        assertNull(response.getBody().getErrors());
    }
}
