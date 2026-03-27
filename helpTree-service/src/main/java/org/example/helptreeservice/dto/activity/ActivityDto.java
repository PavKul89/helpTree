package org.example.helptreeservice.dto.activity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityDto {
    private String type;
    private String typeLabel;
    private String emoji;
    private String title;
    private String description;
    private LocalDateTime timestamp;
    private Long relatedUserId;
    private String relatedUserName;
    private Long relatedPostId;
    private String relatedPostTitle;
    private String category;
    private String status;
}
