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
    private String authorAvatarUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long userId;
    private Double authorRating;
    private String userCity;
    private Double latitude;
    private Double longitude;
    private Long helperId;
    private String helperName;
    private PostStatus status;
    private List<String> imageUrls;
    /** Exact boost expiry; owner/admin only. Never part of the public DTO. */
    private LocalDateTime boostedUntil;
    private boolean boosted;
    private String authorNicknameColor;
    private Integer commentsCount;
    private Integer viewsCount;
    private Integer responsesCount;
}
