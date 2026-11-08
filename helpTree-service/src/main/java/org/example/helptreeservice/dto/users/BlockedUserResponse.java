package org.example.helptreeservice.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.UserBlockScope;

import java.time.LocalDateTime;

/** One entry of {@code GET /api/users/me/blocked}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "BlockedUser", description = "One entry of GET /api/users/me/blocked")
public class BlockedUserResponse {

    private Long id;
    private Long userId;
    private String name;
    private String avatarUrl;
    private UserBlockScope scope;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
