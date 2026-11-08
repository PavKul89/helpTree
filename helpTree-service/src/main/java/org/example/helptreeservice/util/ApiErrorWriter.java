package org.example.helptreeservice.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.helptreeservice.dto.error.ErrorResponse;
import org.example.helptreeservice.dto.error.FieldErrorDto;
import org.example.helptreeservice.enums.ErrorType;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Serialises the shared {@link ErrorResponse} from servlet filters, where MVC's
 * {@code @RestControllerAdvice} is not in play (security, rate limiting).
 *
 * <p>The mapper is deliberately local: the application does not expose a shared
 * ObjectMapper bean, and filter error bodies must stay independent of MVC
 * message-converter configuration.</p>
 */
public final class ApiErrorWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ApiErrorWriter() {
    }

    public static void write(HttpServletRequest request,
                             HttpServletResponse response,
                             HttpStatus status,
                             ErrorType type,
                             String message) throws IOException {
        write(request, response, status, type, message, null);
    }

    public static void write(HttpServletRequest request,
                             HttpServletResponse response,
                             HttpStatus status,
                             ErrorType type,
                             String message,
                             List<FieldErrorDto> fieldErrors) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        LogContext.setErrorType(type.getCode());
        try {
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());

            ErrorResponse body = ErrorResponse.builder()
                    .status(status.value())
                    .code(type.getCode())
                    .message(message)
                    .traceId(TraceIds.current(request))
                    .fieldErrors(fieldErrors != null ? fieldErrors : List.of())
                    .path(request != null ? request.getRequestURI() : null)
                    .build();

            MAPPER.writeValue(response.getWriter(), body);
        } finally {
            LogContext.setErrorType(null);
        }
    }
}
