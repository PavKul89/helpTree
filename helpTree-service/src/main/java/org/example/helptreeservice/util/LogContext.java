package org.example.helptreeservice.util;

import org.slf4j.MDC;

/**
 * Central place for the diagnostic MDC fields the logging layouts render.
 *
 * <p>Only opaque identifiers are stored: a numeric user id, the stable error
 * code and the request correlation id. Names, email addresses, phone numbers,
 * tokens and complaint text never enter MDC, so they cannot leak through a
 * layout, a log aggregator or an exception report.</p>
 */
public final class LogContext {

    public static final String USER_ID = "userId";
    public static final String ERROR_TYPE = "errorType";

    private LogContext() {
    }

    public static void setUserId(Long userId) {
        if (userId == null) {
            MDC.remove(USER_ID);
        } else {
            MDC.put(USER_ID, Long.toString(userId));
        }
    }

    public static void setErrorType(String code) {
        if (code == null || code.isBlank()) {
            MDC.remove(ERROR_TYPE);
        } else {
            MDC.put(ERROR_TYPE, code);
        }
    }

    /** Clears the fields owned by the application. Tracing fields belong to micrometer. */
    public static void clear() {
        MDC.remove(USER_ID);
        MDC.remove(ERROR_TYPE);
    }
}
