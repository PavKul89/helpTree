package org.example.helptreeservice.dto.comments;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentResponse {

    private Long id;
    private Long postId;
    private Long userId;
    private String userName;
    private String userAvatarUrl;

    /** Null for a tombstone; clients render the deletion state instead. */
    private String content;

    private Long parentCommentId;
    private Integer depth;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private boolean deleted;
    private boolean deletedByAuthor;
    private LocalDateTime deletedAt;
}
