package org.example.helptreeservice.dto.users;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.UserBlockScope;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBlockResponse {

    private Long id;
    private Long blockerId;
    private Long blockedId;
    private UserBlockScope scope;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean active;
}
