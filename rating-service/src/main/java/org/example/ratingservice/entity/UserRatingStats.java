package org.example.ratingservice.entity;

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

    @Column(name = "total_helps_given")
    private Long totalHelpsGiven = 0L;

    @Column(name = "total_helps_received")
    private Long totalHelpsReceived = 0L;

    @Column(name = "successful_helps")
    private Long successfulHelps = 0L;

    @Column(name = "cancelled_helps")
    private Long cancelledHelps = 0L;

    @Column(name = "average_response_time")
    private Double averageResponseTime; // Среднее время реакции в минутах

    @Column(name = "last_calculated")
    private LocalDateTime lastCalculated;

    @Column(name = "current_rating")
    private Double currentRating;

    @Column(name = "rating_trend")
    private String ratingTrend; // UP, DOWN, STABLE

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
