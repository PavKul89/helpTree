package org.example.helptreeservice.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.helptreeservice.dto.error.ErrorResponse;
import org.example.helptreeservice.dto.error.FieldErrorDto;
import org.example.helptreeservice.enums.ErrorType;
import org.example.helptreeservice.util.ApiErrorWriter;
import org.example.helptreeservice.util.TraceIds;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockHttpServletRequest request(String uri) {
        return new MockHttpServletRequest("GET", uri);
    }

    @Test
    void notFound_shouldReturn404WithStableCode() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFoundException(
                new NotFoundException("Пост не найден"), request("/api/posts/1"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().getStatus());
        assertEquals(ErrorType.NOT_FOUND.getCode(), response.getBody().getCode());
        assertEquals("Пост не найден", response.getBody().getMessage());
        assertEquals("/api/posts/1", response.getBody().getPath());
    }

    @Test
    void forbidden_shouldReturn403() {
        ResponseEntity<ErrorResponse> response = handler.handleForbiddenException(
                new ForbiddenException("Чужой ресурс"), request("/api/users/2"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(403, response.getBody().getStatus());
        assertEquals(ErrorType.FORBIDDEN.getCode(), response.getBody().getCode());
    }

    @Test
    void accessDenied_shouldReturn403WithoutInternalDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleAccessDeniedException(
                new AccessDeniedException("Access is denied for user admin with authority ROLE_USER"),
                request("/api/admin"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(ErrorType.FORBIDDEN.getCode(), response.getBody().getCode());
        assertEquals("Доступ запрещен", response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("ROLE_USER"));
    }

    @Test
    void unauthorized_shouldReturn401() {
        ResponseEntity<ErrorResponse> response = handler.handleUnauthorizedException(
                new UnauthorizedException("Вы не авторизованы"), request("/api/users/me"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().getStatus());
        assertEquals(ErrorType.UNAUTHORIZED.getCode(), response.getBody().getCode());
    }

    @Test
    void blockedAccount_shouldReturn403WithDedicatedCode() {
        ResponseEntity<ErrorResponse> response = handler.handleAccountBlockedException(
                new AccountBlockedException("Аккаунт заблокирован до завтра"), request("/api/posts"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(ErrorType.ACCOUNT_BLOCKED.getCode(), response.getBody().getCode());
        assertEquals("Аккаунт заблокирован до завтра", response.getBody().getMessage());
    }

    @Test
    void conflict_shouldReturn409() {
        ResponseEntity<ErrorResponse> response = handler.handleConflictException(
                new ConflictException("Уже существует"), request("/api/posts"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().getStatus());
        assertEquals(ErrorType.CONFLICT.getCode(), response.getBody().getCode());
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
                new HttpRequestMethodNotSupportedException("PATCH", List.of("GET"));

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex, request("/api/posts"));

        assertEquals(HttpStatus.METHOD_NOT_ALLOWED, response.getStatusCode());
        assertEquals(405, response.getBody().getStatus());
        assertEquals(ErrorType.METHOD_NOT_ALLOWED.getCode(), response.getBody().getCode());
    }

    @Test
    void mediaTypeNotSupported_shouldReturn415() {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException("text/plain");

        ResponseEntity<ErrorResponse> response = handler.handleMediaTypeNotSupported(ex, request("/api/posts"));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        assertEquals(415, response.getBody().getStatus());
        assertEquals(ErrorType.UNSUPPORTED_MEDIA_TYPE.getCode(), response.getBody().getCode());
    }

    @Test
    void uploadTooLarge_shouldReturn413() {
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(10_000_000L);

        ResponseEntity<ErrorResponse> response = handler.handleMaxUploadSizeExceeded(ex, request("/api/images"));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertEquals(413, response.getBody().getStatus());
        assertEquals(ErrorType.PAYLOAD_TOO_LARGE.getCode(), response.getBody().getCode());
    }

    @Test
    void validation_shouldReturnFieldErrors() {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "createUserRequest");
        bindingResult.addError(new FieldError(
                "createUserRequest", "email", "invalid@", false,
                new String[]{"Email"}, null, "Некорректный email"));
        bindingResult.addError(new FieldError(
                "createUserRequest", "password", "Пароль слишком короткий"));

        ResponseEntity<ErrorResponse> response = handler.handleBindException(
                new BindException(bindingResult), request("/api/auth/register"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(ErrorType.VALIDATION_FAILED.getCode(), response.getBody().getCode());
        List<FieldErrorDto> fieldErrors = response.getBody().getFieldErrors();
        assertEquals(2, fieldErrors.size());
        assertEquals("email", fieldErrors.get(0).field());
        assertEquals("Некорректный email", fieldErrors.get(0).message());
        assertEquals("Email", fieldErrors.get(0).code());
        assertEquals("password", fieldErrors.get(1).field());
    }

    @Test
    void nonValidationError_shouldReturnEmptyFieldErrors() {
        ResponseEntity<ErrorResponse> response = handler.handleNotFoundException(
                new NotFoundException("Нет"), request("/api/posts/1"));

        assertNotNull(response.getBody().getFieldErrors());
        assertTrue(response.getBody().getFieldErrors().isEmpty());
    }

    @Test
    void traceId_shouldBeCopiedFromRequestAttribute() {
        MockHttpServletRequest request = request("/api/posts");
        request.setAttribute(TraceIds.REQUEST_ATTRIBUTE, "trace-123");

        ResponseEntity<ErrorResponse> response = handler.handleNotFoundException(
                new NotFoundException("Нет"), request);

        assertEquals("trace-123", response.getBody().getTraceId());
    }

    @Test
    void rateLimit_shouldSetRetryAfterAnd429() {
        ResponseEntity<ErrorResponse> response = handler.handleRateLimitExceeded(
                new RateLimitExceededException("Слишком часто", 42), request("/api/auth/login"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals(ErrorType.TOO_MANY_REQUESTS.getCode(), response.getBody().getCode());
        assertEquals("42", response.getHeaders().getFirst("Retry-After"));
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
        assertEquals(ErrorType.INTERNAL_SERVER_ERROR.getCode(), response.getBody().getCode());
        assertEquals("Произошла непредвиденная ошибка", response.getBody().getMessage());
        assertFalse(response.getBody().getMessage().contains("PSQLException"));
        assertFalse(response.getBody().getMessage().contains("users"));
        assertTrue(response.getBody().getFieldErrors().isEmpty());
    }

    @Test
    void filterErrorWriter_shouldMatchMvcEnvelope() throws Exception {
        MockHttpServletRequest request = request("/api/auth/login");
        request.setAttribute(TraceIds.REQUEST_ATTRIBUTE, "trace-filter");
        MockHttpServletResponse response = new MockHttpServletResponse();

        ApiErrorWriter.write(request, response, HttpStatus.UNAUTHORIZED,
                ErrorType.UNAUTHORIZED, "Требуется авторизация");

        assertEquals(401, response.getStatus());
        JsonNode body = new ObjectMapper().readTree(response.getContentAsString());
        assertEquals(401, body.get("status").asInt());
        assertEquals(ErrorType.UNAUTHORIZED.getCode(), body.get("code").asText());
        assertEquals("Требуется авторизация", body.get("message").asText());
        assertEquals("trace-filter", body.get("traceId").asText());
        assertEquals("/api/auth/login", body.get("path").asText());
        assertTrue(body.get("fieldErrors").isArray());
        assertTrue(body.get("fieldErrors").isEmpty());
    }
}
