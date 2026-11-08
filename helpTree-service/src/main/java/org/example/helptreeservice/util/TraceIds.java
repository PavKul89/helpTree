package org.example.helptreeservice.util;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Resolves the request correlation id used in logs and in the error envelope.
 *
 * <p>A caller-supplied {@code X-Request-Id} is accepted only when it is short
 * and contains safe characters; otherwise a fresh id is generated. This keeps
 * log injection and oversized headers out of the contract.</p>
 */
public final class TraceIds {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    public static final String REQUEST_ATTRIBUTE = TraceIds.class.getName() + ".traceId";

    private static final Pattern SAFE_VALUE = Pattern.compile("[A-Za-z0-9._:-]{1,100}");

    private TraceIds() {
    }

    /** The id assigned to the current request, or {@code null} outside a request. */
    public static String current(HttpServletRequest request) {
        if (request != null) {
            Object attribute = request.getAttribute(REQUEST_ATTRIBUTE);
            if (attribute instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        String mdcValue = MDC.get(MDC_KEY);
        if (mdcValue != null && !mdcValue.isBlank()) {
            return mdcValue;
        }
        if (request != null) {
            String header = request.getHeader(HEADER);
            if (isSafe(header)) {
                return header.trim();
            }
        }
        return null;
    }

    /** The id assigned to the current thread, or {@code null}. */
    public static String current() {
        return MDC.get(MDC_KEY);
    }

    /** Accepts a well-formed inbound id or generates a new one. */
    public static String resolveOrGenerate(String inbound) {
        if (isSafe(inbound)) {
            return inbound.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static boolean isSafe(String value) {
        return value != null && SAFE_VALUE.matcher(value.trim()).matches();
    }
}
