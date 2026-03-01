package com.example.helpTree.dto;

import com.example.helpTree.enums.PostStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class PostDto {
    private Long id;
    private String title;
    private String description;
    private String authorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long userId;
    private String userEmail;
    private Double userRating;
    private Long helperId;
    private String helperName;

    // 👇 ДОБАВИТЬ
    private PostStatus status;
}
