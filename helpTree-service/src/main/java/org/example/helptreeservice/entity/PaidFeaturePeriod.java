package org.example.helptreeservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.example.helptreeservice.enums.PaidFeatureType;

import java.time.LocalDateTime;

/**
 * Append-only period for a paid feature. A {@code null} end means the feature
 * does not expire (nickname colour); VIP and boosts always have an end.
 */
@Entity
@Table(name = "paid_feature_periods", indexes = {
    @Index(name = "idx_paid_feature_periods_user_started", columnList = "user_id, started_at"),
    @Index(name = "idx_paid_feature_periods_active_end", columnList = "ends_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaidFeaturePeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 30)
    private PaidFeatureType feature;

    /** Transaction id for purchases, post id for boosts, null otherwise. */
    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
