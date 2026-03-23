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
    private String content;
    private Long parentCommentId;
    private LocalDateTime createdAt;
}
