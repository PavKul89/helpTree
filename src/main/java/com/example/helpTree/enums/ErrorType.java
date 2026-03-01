package com.example.helpTree.enums;

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
