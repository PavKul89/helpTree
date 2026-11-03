package org.example.helptreeservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Append-only audit of administrative coin corrections. Unlike the wallet
 * ledger (which is user-facing history), this row records who made the change,
 * why, and the exact balance before/after.
 */
@Entity
@Table(name = "admin_coin_audits", indexes = {
        @Index(name = "idx_admin_coin_audits_user", columnList = "user_id, created_at"),
        @Index(name = "idx_admin_coin_audits_admin", columnList = "admin_id, created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCoinAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "balance_before", nullable = false)
    private Long balanceBefore;

    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "transaction_id", unique = true)
    private Long transactionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
