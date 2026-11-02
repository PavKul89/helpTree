package org.example.helptreeservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.helptreeservice.enums.AccountTokenType;

import java.time.LocalDateTime;

/**
 * Single-use token for email verification, password reset and email change.
 * Only the SHA-256 hash of the raw token is persisted.
 */
@Entity
@Table(name = "account_tokens", indexes = {
    @Index(name = "idx_account_tokens_user_type", columnList = "user_id,type"),
    @Index(name = "idx_account_tokens_expires_at", columnList = "expires_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AccountTokenType type;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /**
     * For EMAIL_CHANGE this stores the requested new address. Other token types keep it null.
     */
    @Column(length = 320)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    public boolean isUsable(LocalDateTime now) {
        return usedAt == null && revokedAt == null && expiresAt != null && expiresAt.isAfter(now);
    }
}
