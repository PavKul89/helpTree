package org.example.helptreeservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.example.helptreeservice.enums.UserBlockSource;

import java.time.LocalDateTime;

/**
 * A moderation account block. It is deliberately separate from the debt-related
 * fields on {@link User} ({@code blockedUntil}, {@code debtCount}); a report
 * never creates a debt block and a debt block is never lifted by moderation.
 */
@Entity
@Table(name = "user_blocks", indexes = {
    @Index(name = "idx_user_blocks_user_started", columnList = "user_id, starts_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"user", "blockedBy", "liftedBy"})
@ToString(exclude = {"user", "blockedBy", "liftedBy"})
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_by_id")
    private User blockedBy;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserBlockSource source = UserBlockSource.ADMIN;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "lifted_at")
    private LocalDateTime liftedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lifted_by_id")
    private User liftedBy;

    @Column(name = "lift_reason", length = 1000)
    private String liftReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (startsAt == null) {
            startsAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }

    public boolean isActive() {
        return liftedAt == null && (endsAt == null || endsAt.isAfter(LocalDateTime.now()));
    }
}
