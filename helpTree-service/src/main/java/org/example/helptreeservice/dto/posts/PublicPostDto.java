package org.example.helptreeservice.dto.posts;

import lombok.Builder;
import lombok.Data;
import org.example.helptreeservice.enums.PostStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Public post projection returned by {@code GET /api/posts/**}.
 *
 * <p>This DTO intentionally has no moderation or boost-expiry fields. Exact
 * coordinates are rounded by the service; the owner and admins receive the
 * full {@link PostDto} from an authenticated endpoint instead.</p>
 */
@Data
@Builder
public class PublicPostDto {
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
    /** Whether the post is currently promoted; the exact expiry is private. */
    private boolean boosted;
    private String authorNicknameColor;
    private Integer commentsCount;
}
