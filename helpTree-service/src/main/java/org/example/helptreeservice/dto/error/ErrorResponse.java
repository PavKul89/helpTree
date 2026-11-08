package org.example.helptreeservice.dto.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The single error envelope returned by every endpoint, including responses
 * produced by security filters and the rate limiter (not only MVC exceptions).
 *
 * <p>Contract fields are {@code status/code/message/traceId/fieldErrors}.
 * {@code timestamp} and {@code path} are additional diagnostic fields and must
 * not be required by clients.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ErrorResponse", description = "Stable error envelope shared by all API endpoints")
public class ErrorResponse {

    @Builder.Default
    @Schema(description = "Server time in ISO-8601 format", example = "2026-11-08T12:34:56Z")
    private String timestamp = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .format(OffsetDateTime.now(ZoneOffset.UTC));

    @Schema(description = "HTTP status code", example = "400", requiredMode = Schema.RequiredMode.REQUIRED)
    private int status;

    @Schema(description = "Stable machine-readable error code", example = "VALIDATION_FAILED",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @Schema(description = "Human-readable, non-sensitive explanation", example = "Проверьте правильность заполнения полей",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(description = "Request correlation id; also returned in the X-Request-Id response header",
            example = "3f6c1f0e1b7a4f7e9a8c2d5b6e7f8091", requiredMode = Schema.RequiredMode.REQUIRED)
    private String traceId;

    @ArraySchema(arraySchema = @Schema(description = "Field-level validation failures; empty for non-validation errors",
            requiredMode = Schema.RequiredMode.REQUIRED),
            schema = @Schema(implementation = FieldErrorDto.class))
    private List<FieldErrorDto> fieldErrors;

    @Schema(description = "Request path", example = "/api/posts")
    private String path;
}
