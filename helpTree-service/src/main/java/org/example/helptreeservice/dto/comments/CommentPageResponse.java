package org.example.helptreeservice.dto.comments;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One page of discussion threads. {@code totalElements}/{@code totalPages}
 * describe root comments, while {@code content} also contains their loaded
 * descendants so a page never starts with an orphaned reply.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentPageResponse {

    private List<CommentResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasMore;

    /** Number of non-deleted comments on the post (the post counter). */
    private long totalComments;
}
