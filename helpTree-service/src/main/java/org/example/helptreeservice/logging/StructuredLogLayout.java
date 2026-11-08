package org.example.helptreeservice.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.LayoutBase;
import org.example.helptreeservice.util.LogContext;
import org.example.helptreeservice.util.SensitiveDataMasker;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One-line JSON layout for the {@code prod} profile.
 *
 * <p>Every record carries the operational keys an aggregator needs to filter and
 * correlate: time, level, component, trace/span id, request id, user id and the
 * stable error type. The message and exception are masked with
 * {@link SensitiveDataMasker} because logs are retained and searchable long
 * after the request ended.</p>
 *
 * <p>JSON is appended manually instead of through an ObjectMapper: a layout is
 * instantiated by Logback before Spring starts, so it must not depend on a
 * configured bean or on Jackson's module discovery.</p>
 */
public class StructuredLogLayout extends LayoutBase<ILoggingEvent> {

    @Override
    public String doLayout(ILoggingEvent event) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("time", Instant.ofEpochMilli(event.getTimeStamp()).toString());
        fields.put("level", event.getLevel().toString());
        fields.put("component", event.getLoggerName());
        fields.put("thread", event.getThreadName());
        putIfPresent(fields, "traceId", value(event, "traceId"));
        putIfPresent(fields, "spanId", value(event, "spanId"));
        putIfPresent(fields, "requestId", value(event, "requestId"));
        putIfPresent(fields, "userId", value(event, LogContext.USER_ID));
        putIfPresent(fields, "errorType", value(event, LogContext.ERROR_TYPE));
        fields.put("message", SensitiveDataMasker.maskSensitive(event.getFormattedMessage()));

        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            fields.put("exception", SensitiveDataMasker.maskSensitive(
                    ThrowableProxyUtil.asString(throwable)));
        }

        StringBuilder json = new StringBuilder(256);
        json.append('{');
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            appendString(json, entry.getKey());
            json.append(':');
            appendString(json, entry.getValue());
        }
        json.append('}').append('\n');
        return json.toString();
    }

    private String value(ILoggingEvent event, String key) {
        String fromEvent = event.getMDCPropertyMap() == null
                ? null
                : event.getMDCPropertyMap().get(key);
        if (fromEvent != null && !fromEvent.isBlank()) {
            return fromEvent;
        }
        return MDC.get(key);
    }

    private void putIfPresent(Map<String, String> fields, String key, String value) {
        if (value != null && !value.isBlank()) {
            fields.put(key, value);
        }
    }

    private void appendString(StringBuilder json, String value) {
        json.append('"');
        if (value != null) {
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                switch (c) {
                    case '"' -> json.append("\\\"");
                    case '\\' -> json.append("\\\\");
                    case '\n' -> json.append("\\n");
                    case '\r' -> json.append("\\r");
                    case '\t' -> json.append("\\t");
                    case '\b' -> json.append("\\b");
                    case '\f' -> json.append("\\f");
                    default -> {
                        if (c < 0x20) {
                            json.append(String.format("\\u%04x", (int) c));
                        } else {
                            json.append(c);
                        }
                    }
                }
            }
        }
        json.append('"');
    }
}
