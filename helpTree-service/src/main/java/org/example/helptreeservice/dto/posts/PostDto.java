package org.example.helptreeservice.dto.posts;

import org.example.helptreeservice.enums.PostStatus;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class PostDto {
    private Long id;
    private String title;
    private String description;
    private String category;
    private String authorName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long userId;
    private String userEmail;
    private Double userRating;
    private Long helperId;
    private String helperName;
    private PostStatus status;
    private List<String> imageUrls;
}
