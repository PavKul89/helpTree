package org.example.helptreeservice.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Administrative projection of a login session. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSessionDto {
    private UUID id;
    private LocalDateTime createdAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime expiresAt;
    private String ipAddress;
    private String userAgent;
    private String deviceName;
    private boolean current;
}
