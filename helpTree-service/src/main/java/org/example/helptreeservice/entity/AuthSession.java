package org.example.helptreeservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Server-side login session. Access tokens carry the session id and stop working
 * as soon as the session is revoked, even if the JWT itself has not expired yet.
 */
@Entity
@Table(name = "auth_sessions", indexes = {
    @Index(name = "idx_auth_sessions_user_id", columnList = "user_id"),
    @Index(name = "idx_auth_sessions_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthSession {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_reason", length = 50)
    private String revokedReason;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    public boolean isActive(LocalDateTime now) {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }
}
