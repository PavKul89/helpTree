package com.example.helpTree.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDto {
    private Long id;
    private String title;
    private String description;
    private String authorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Информация об авторе
    private Long userId;
    private String userEmail;
    private Double userRating;
}
