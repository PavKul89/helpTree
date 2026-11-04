package org.example.helptreeservice.dto.comments;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a comment mutation. Returning the fresh counter lets the client
 * update the post header without reloading the whole discussion.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentActionResponse {

    private CommentResponse comment;
    private long totalComments;
}
