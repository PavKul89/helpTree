package org.example.helptreeservice.enums;

/**
 * Stable machine-readable error codes used in the {@code code} field of
 * {@code ErrorResponse}.
 *
 * <p>The enum name is part of the public API contract: frontend and mobile
 * clients may switch on it. Never rename an existing constant; add a new one
 * and keep the old value as a deprecated alias instead.</p>
 */
public enum ErrorType {
    BAD_REQUEST("BAD_REQUEST", "Bad Request"),
    VALIDATION_FAILED("VALIDATION_FAILED", "Validation Failed"),
    UNAUTHORIZED("UNAUTHORIZED", "Unauthorized"),
    FORBIDDEN("FORBIDDEN", "Forbidden"),
    ACCOUNT_BLOCKED("ACCOUNT_BLOCKED", "Account Blocked"),
    NOT_FOUND("NOT_FOUND", "Not Found"),
    CONFLICT("CONFLICT", "Conflict"),
    PAYLOAD_TOO_LARGE("PAYLOAD_TOO_LARGE", "Payload Too Large"),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "Unsupported Media Type"),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "Method Not Allowed"),
    TOO_MANY_REQUESTS("TOO_MANY_REQUESTS", "Too Many Requests"),
    FEATURE_DISABLED("FEATURE_DISABLED", "Feature Disabled"),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", "Service Unavailable"),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", "Internal Server Error");

    private final String code;
    private final String title;

    ErrorType(String code, String title) {
        this.code = code;
        this.title = title;
    }

    /** Stable value for the {@code code} field. */
    public String getCode() {
        return code;
    }

    /** Human-readable title kept for backwards-compatible logging. */
    public String getTitle() {
        return title;
    }

    @Override
    public String toString() {
        return code;
    }
}
