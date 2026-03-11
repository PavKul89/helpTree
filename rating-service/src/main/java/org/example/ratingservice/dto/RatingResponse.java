package org.example.ratingservice.dto;

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
    private Long totalHelps;
    private Long totalReceivedHelps;
    private Double successRate;
}
