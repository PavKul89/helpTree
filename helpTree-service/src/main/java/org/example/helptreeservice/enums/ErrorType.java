package org.example.helptreeservice.enums;

/**
 * Типы ошибок/теги для поля `error` в ErrorResponse.
 * Позволяет избежать магических строк в обработчиках ошибок и централизовать названия.
 */
@SuppressWarnings("unused")
public enum ErrorType {
    NOT_FOUND("Not Found"),
    CONFLICT("Conflict"),
    BAD_REQUEST("Bad Request"),
    VALIDATION_FAILED("Validation Failed"),
    UNAUTHORIZED("Unauthorized"),
    FORBIDDEN("Forbidden"),
    METHOD_NOT_ALLOWED("Method Not Allowed"),
    UNSUPPORTED_MEDIA_TYPE("Unsupported Media Type"),
    INTERNAL_SERVER_ERROR("Internal Server Error");

    private final String title;

    ErrorType(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public String toString() {
        return title;
    }
}
