package org.example.helptreeservice.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.UserBlockScope;

import java.time.LocalDateTime;

/**
 * Request body for {@code POST /api/users/{id}/block}. Both fields are
 * optional: an empty body means a permanent block in every area.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "BlockUserRequest",
        description = "Both fields are optional; an empty body means a permanent block in every area")
public class BlockUserRequest {

    @Schema(description = "Affected area", example = "ALL")
    private UserBlockScope scope;

    /** {@code null} means permanent. A value must be in the future. */
    @Schema(description = "Block expiry; null means permanent", example = "2026-12-31T23:59:59")
    private LocalDateTime expiresAt;
}
