package org.example.helptreeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Public rating projection returned by {@code GET /api/ratings/**}.
 *
 * <p>The internal {@code ratingComponents} map (response time, per-component
 * counters) and the raw help-record counters are calculation details used by
 * admins and the rating history. Only the aggregate reputation fields that a
 * public profile may show are exposed here.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicRatingResponse {
    private Long userId;
    private String userName;
    private Double overallRating;
    private String level;
    private LocalDateTime calculatedAt;
    /** Confirmed help acts given by this user. */
    private Long totalHelps;
    /** Confirmed help acts received by this user. */
    private Long totalReceivedHelps;
    private Long reviewCount;
    private Double averageReviewRating;
    private Double successRate;

    public static PublicRatingResponse from(RatingResponse rating) {
        if (rating == null) {
            return null;
        }
        return PublicRatingResponse.builder()
                .userId(rating.getUserId())
                .userName(rating.getUserName())
                .overallRating(rating.getOverallRating())
                .level(rating.getLevel())
                .calculatedAt(rating.getCalculatedAt())
                .totalHelps(rating.getTotalHelps())
                .totalReceivedHelps(rating.getTotalReceivedHelps())
                .reviewCount(rating.getReviewCount())
                .averageReviewRating(rating.getAverageReviewRating())
                .successRate(rating.getSuccessRate())
                .build();
    }
}
