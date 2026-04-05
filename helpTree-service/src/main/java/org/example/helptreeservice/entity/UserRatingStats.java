package org.example.helptreeservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_rating_stats",
        uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRatingStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Builder.Default
    @Column(name = "total_helps_given")
    private Long totalHelpsGiven = 0L;

    @Builder.Default
    @Column(name = "total_helps_received")
    private Long totalHelpsReceived = 0L;

    @Builder.Default
    @Column(name = "successful_helps")
    private Long successfulHelps = 0L;

    @Builder.Default
    @Column(name = "cancelled_helps")
    private Long cancelledHelps = 0L;

    @Column(name = "average_response_time")
    private Double averageResponseTime;

    @Column(name = "last_calculated")
    private LocalDateTime lastCalculated;

    @Builder.Default
    @Column(name = "current_rating")
    private Double currentRating = 0.0;

    @Builder.Default
    @Column(name = "rating_trend")
    private String ratingTrend = "STABLE";

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();

        if (lastCalculated == null) {
            lastCalculated = LocalDateTime.now();
        }
    }
}
