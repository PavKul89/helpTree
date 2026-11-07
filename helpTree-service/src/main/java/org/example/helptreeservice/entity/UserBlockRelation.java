package org.example.helptreeservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.helptreeservice.enums.UserBlockScope;

import java.time.LocalDateTime;

/**
 * A block created by one user against another.
 *
 * <p>This is deliberately separate from {@link UserBlock} (the moderation/debt
 * journal) and from the {@code blockedUntil} field on {@link User}. Those
 * describe an account-level sanction; this entity only describes which areas of
 * interaction one user has closed for another. An expired row stays in the table
 * so the pair keeps its single identity, but it is ignored by every query that
 * checks "is the block active now".</p>
 */
@Entity
@Table(name = "user_blocks_relations", indexes = {
    @Index(name = "idx_user_blocks_relations_blocker", columnList = "blocker_id, expires_at"),
    @Index(name = "idx_user_blocks_relations_blocked", columnList = "blocked_id, expires_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"blocker", "blocked"})
@ToString(exclude = {"blocker", "blocked"})
public class UserBlockRelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocker_id", nullable = false)
    private User blocker;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "blocked_id", nullable = false)
    private User blocked;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    private UserBlockScope scope;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    void applyDefaults() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (scope == null) {
            scope = UserBlockScope.ALL;
        }
    }

    /** A block without an expiry is permanent. */
    public boolean isActiveAt(LocalDateTime now) {
        return expiresAt == null || expiresAt.isAfter(now);
    }
}
