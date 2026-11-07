package org.example.helptreeservice.dto.users;

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
public class BlockUserRequest {

    private UserBlockScope scope;

    /** {@code null} means permanent. A value must be in the future. */
    private LocalDateTime expiresAt;
}
