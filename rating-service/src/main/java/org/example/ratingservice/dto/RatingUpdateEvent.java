package org.example.ratingservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingUpdateEvent {
    private Long userId;
    private Double oldRating;
    private Double newRating;
    private LocalDateTime updatedAt;
    private String reason;
}
