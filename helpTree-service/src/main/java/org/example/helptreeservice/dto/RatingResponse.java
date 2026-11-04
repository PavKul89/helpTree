package org.example.helptreeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingResponse {
    private Long userId;
    private String userName;
    private Double overallRating;
    private Map<String, Double> ratingComponents;
    private String level;
    private LocalDateTime calculatedAt;

    /** Confirmed help acts given by this user. */
    private Long totalHelps;

    /** Every non-deleted help record where this user was the helper. */
    private Long totalHelpRecords;

    /** ACCEPTED or COMPLETED help records given by this user. */
    private Long activeHelps;

    private Long cancelledHelps;
    private Long totalReceivedHelps;
    private Long reviewCount;
    private Double averageReviewRating;
    private Double successRate;
}
