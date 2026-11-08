package org.example.helptreeservice.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.error.ErrorResponse;
import org.example.helptreeservice.dto.error.FieldErrorDto;
import org.example.helptreeservice.enums.ErrorType;
import org.example.helptreeservice.util.LogContext;
import org.example.helptreeservice.util.TraceIds;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Single source of the error contract for MVC endpoints.
 *
 * <p>Every branch returns {@link ErrorResponse} with a stable {@code code} and
 * the current request's {@code traceId}. Internal exception messages, SQL,
 * class names and stack traces are never copied into the response; they stay in
 * the server log, correlated by the same trace id.</p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.NOT_FOUND.getCode());
        log.info("Resource not found: {}", ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ErrorType.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflictException(ConflictException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.CONFLICT.getCode());
        log.info("Conflict: {}", ex.getMessage());
        return error(HttpStatus.CONFLICT, ErrorType.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequestException(BadRequestException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.BAD_REQUEST.getCode());
        log.info("Bad request: {}", ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbiddenException(ForbiddenException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.FORBIDDEN.getCode());
        log.warn("Access denied: {}", ex.getMessage());
        return error(HttpStatus.FORBIDDEN, ErrorType.FORBIDDEN, ex.getMessage(), request);
    }

    @ExceptionHandler(AccountBlockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountBlockedException(
            AccountBlockedException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.ACCOUNT_BLOCKED.getCode());
        log.warn("Blocked account for {}: {}", request.getRequestURI(), ex.getMessage());
        return error(HttpStatus.FORBIDDEN, ErrorType.ACCOUNT_BLOCKED, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.FORBIDDEN.getCode());
        log.warn("Spring Security access denied for {}: {}", request.getRequestURI(), ex.getMessage());
        return error(HttpStatus.FORBIDDEN, ErrorType.FORBIDDEN, "Доступ запрещен", request);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedException(UnauthorizedException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.UNAUTHORIZED.getCode());
        log.warn("Unauthorized: {}", ex.getMessage());
        return error(HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.TOO_MANY_REQUESTS.getCode());
        log.warn("Rate limit exceeded for {}: {}", request.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(ex.getRetryAfterSeconds()))
                .body(body(HttpStatus.TOO_MANY_REQUESTS, ErrorType.TOO_MANY_REQUESTS,
                        ex.getMessage(), request, List.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.VALIDATION_FAILED.getCode());
        List<FieldErrorDto> fieldErrors = toFieldErrors(ex.getBindingResult());
        log.warn("Validation failed for request {}: {}", request.getRequestURI(), fieldErrors);
        return error(HttpStatus.BAD_REQUEST, ErrorType.VALIDATION_FAILED,
                "Проверьте правильность заполнения полей", request, fieldErrors);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.VALIDATION_FAILED.getCode());
        List<FieldErrorDto> fieldErrors = toFieldErrors(ex.getBindingResult());
        log.warn("Binding failed for request {}: {}", request.getRequestURI(), fieldErrors);
        return error(HttpStatus.BAD_REQUEST, ErrorType.VALIDATION_FAILED,
                "Проверьте правильность заполнения полей", request, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.VALIDATION_FAILED.getCode());
        List<FieldErrorDto> fieldErrors = new ArrayList<>();
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            fieldErrors.add(new FieldErrorDto(
                    fieldName(violation.getPropertyPath().toString()),
                    violation.getMessage(),
                    null));
        }

        log.warn("Constraint violations for request {}: {}", request.getRequestURI(), fieldErrors);
        return error(HttpStatus.BAD_REQUEST, ErrorType.VALIDATION_FAILED,
                "Проверьте правильность заполнения полей", request, fieldErrors);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<ErrorResponse> handleWebExchangeBindException(WebExchangeBindException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.VALIDATION_FAILED.getCode());
        List<FieldErrorDto> fieldErrors = toFieldErrors(ex.getBindingResult());
        log.warn("WebExchange binding failed for request {}: {}", request.getRequestURI(), fieldErrors);
        return error(HttpStatus.BAD_REQUEST, ErrorType.VALIDATION_FAILED,
                "Проверьте правильность заполнения полей", request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.BAD_REQUEST.getCode());
        log.warn("Malformed request for {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return error(HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST, "Неверный формат запроса", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.BAD_REQUEST.getCode());
        String message = String.format("Параметр '%s' должен иметь тип %s", ex.getName(),
                ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown");
        log.warn("Type mismatch for request {}: {}", request.getRequestURI(), message);
        return error(HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.BAD_REQUEST.getCode());
        String message = String.format("Обязательный параметр '%s' отсутствует", ex.getParameterName());
        log.warn("Missing parameter for request {}: {}", request.getRequestURI(), message);
        return error(HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestHeader(
            MissingRequestHeaderException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.BAD_REQUEST.getCode());
        String message = String.format("Обязательный заголовок '%s' отсутствует", ex.getHeaderName());
        log.warn("Missing header for request {}: {}", request.getRequestURI(), message);
        return error(HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.CONFLICT.getCode());
        log.error("Data integrity violation for {}: {}", request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return error(HttpStatus.CONFLICT, ErrorType.CONFLICT,
                "Нарушение целостности данных. Возможно, запись уже существует.", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.BAD_REQUEST.getCode());
        log.warn("Illegal argument for {}: {}", request.getRequestURI(), ex.getMessage());
        return error(HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST, "Некорректный запрос", request);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.BAD_REQUEST.getCode());
        String message = String.format("Обязательная часть запроса '%s' отсутствует", ex.getRequestPartName());
        log.warn("Missing request part for {}: {}", request.getRequestURI(), message);
        return error(HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST, message, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.PAYLOAD_TOO_LARGE.getCode());
        log.warn("File size exceeded for {}: {}", request.getRequestURI(), ex.getMessage());
        return error(HttpStatus.PAYLOAD_TOO_LARGE, ErrorType.PAYLOAD_TOO_LARGE,
                "Размер загружаемого файла превышает максимально допустимый", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.NOT_FOUND.getCode());
        log.info("No static resource for {}: {}", request.getRequestURI(), ex.getMessage());
        return error(HttpStatus.NOT_FOUND, ErrorType.NOT_FOUND, "Ресурс не найден", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.METHOD_NOT_ALLOWED.getCode());
        log.warn("Method {} not supported for {}", ex.getMethod(), request.getRequestURI());
        return error(HttpStatus.METHOD_NOT_ALLOWED, ErrorType.METHOD_NOT_ALLOWED,
                "Метод запроса не поддерживается для этого ресурса", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.UNSUPPORTED_MEDIA_TYPE.getCode());
        log.warn("Unsupported media type {} for {}", ex.getContentType(), request.getRequestURI());
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorType.UNSUPPORTED_MEDIA_TYPE,
                "Неподдерживаемый тип содержимого запроса", request);
    }

    @ExceptionHandler({CompletionException.class, ExecutionException.class})
    public ResponseEntity<ErrorResponse> handleAsyncFailure(Exception ex, HttpServletRequest request) {
        Throwable cause = ex;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof NotFoundException e) {
            return handleNotFoundException(e, request);
        }
        if (cause instanceof ConflictException e) {
            return handleConflictException(e, request);
        }
        if (cause instanceof BadRequestException e) {
            return handleBadRequestException(e, request);
        }
        if (cause instanceof ForbiddenException e) {
            return handleForbiddenException(e, request);
        }
        if (cause instanceof UnauthorizedException e) {
            return handleUnauthorizedException(e, request);
        }
        if (cause instanceof AccountBlockedException e) {
            return handleAccountBlockedException(e, request);
        }
        if (cause instanceof MaxUploadSizeExceededException e) {
            return handleMaxUploadSizeExceeded(e, request);
        }
        if (cause instanceof FeatureDisabledException e) {
            return handleFeatureDisabled(e, request);
        }
        // Unwrap the original exception so the generic handler logs the real
        // cause instead of "CompletionException: ...".
        return handleGenericException(
                cause instanceof Exception exception ? exception : new RuntimeException(cause), request);
    }

    @ExceptionHandler(FeatureDisabledException.class)
    public ResponseEntity<ErrorResponse> handleFeatureDisabled(
            FeatureDisabledException ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.FEATURE_DISABLED.getCode());
        log.warn("Feature {} is disabled for {}", ex.getFeature().getCode(),
                request.getRequestURI());
        return error(HttpStatus.SERVICE_UNAVAILABLE, ErrorType.FEATURE_DISABLED,
                ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        LogContext.setErrorType(ErrorType.INTERNAL_SERVER_ERROR.getCode());
        log.error("Unexpected error for {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ErrorType.INTERNAL_SERVER_ERROR,
                "Произошла непредвиденная ошибка", request);
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status,
                                                ErrorType type,
                                                String message,
                                                HttpServletRequest request) {
        return error(status, type, message, request, List.of());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status,
                                                ErrorType type,
                                                String message,
                                                HttpServletRequest request,
                                                List<FieldErrorDto> fieldErrors) {
        return ResponseEntity.status(status)
                .body(body(status, type, message, request, fieldErrors));
    }

    private ErrorResponse body(HttpStatus status,
                               ErrorType type,
                               String message,
                               HttpServletRequest request,
                               List<FieldErrorDto> fieldErrors) {
        return ErrorResponse.builder()
                .status(status.value())
                .code(type.getCode())
                .message(message)
                .traceId(TraceIds.current(request))
                .fieldErrors(fieldErrors != null ? fieldErrors : List.of())
                .path(request != null ? request.getRequestURI() : null)
                .build();
    }

    private static List<FieldErrorDto> toFieldErrors(BindingResult bindingResult) {
        List<FieldErrorDto> result = new ArrayList<>();
        // LinkedHashMap keeps the first message for a repeated field while the
        // full list still exposes several independent violations.
        Map<String, FieldErrorDto> byFieldAndCode = new LinkedHashMap<>();
        for (ObjectError objectError : bindingResult.getAllErrors()) {
            String field = objectError instanceof FieldError fieldError
                    ? fieldError.getField()
                    : objectError.getObjectName();
            FieldErrorDto dto = new FieldErrorDto(
                    field, objectError.getDefaultMessage(), objectError.getCode());
            byFieldAndCode.putIfAbsent(field + "\u0000" + objectError.getCode(), dto);
        }
        result.addAll(byFieldAndCode.values());
        return result;
    }

    private static String fieldName(String propertyPath) {
        if (propertyPath == null || propertyPath.isBlank()) {
            return "request";
        }
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot >= 0 ? propertyPath.substring(lastDot + 1) : propertyPath;
    }
}
