package org.example.ratingservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "rating_history",
        indexes = {
                @Index(name = "idx_user_id", columnList = "user_id"),
                @Index(name = "idx_calculated_at", columnList = "calculated_at")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "old_rating")
    private Double oldRating;

    @Column(name = "new_rating", nullable = false)
    private Double newRating;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "help_id")
    private Long helpId;

    @Column(name = "rating_components", length = 1000)
    private String ratingComponents;
}