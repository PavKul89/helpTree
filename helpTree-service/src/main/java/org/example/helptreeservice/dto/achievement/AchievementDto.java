package org.example.helptreeservice.dto.achievement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AchievementDto {
    private Long id;
    private String type;
    private String emoji;
    private String name;
    private String description;
    private String rarity;
    private LocalDateTime earnedAt;
    private Integer currentProgress;
    private Integer targetValue;
    private Boolean isEarned;
}
