package org.example.helptreeservice.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.UserBlockScope;

import java.time.LocalDateTime;

/**
 * Viewer-specific block state for a target user.
 *
 * <p>{@code blockedByMe} and {@code blockedMe} are independent: two users may
 * have blocked each other. {@code active} is true when at least one direction
 * still applies. The scope/expiry fields describe the viewer's own block when it
 * exists, otherwise the incoming block.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UserBlockStatus",
        description = "Viewer-specific block state; both directions are independent")
public class UserBlockStatusResponse {

    private Long userId;
    private boolean blockedByMe;
    private boolean blockedMe;
    private boolean active;
    private UserBlockScope scope;
    private LocalDateTime expiresAt;
}
