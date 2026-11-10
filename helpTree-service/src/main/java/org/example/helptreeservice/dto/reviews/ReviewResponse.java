package org.example.helptreeservice.dto.reviews;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ReviewResponse {
    private Long id;
    private Long helpId;
    private Long postId;
    private Long fromUserId;
    private String fromUserName;
    private Long toUserId;
    private String toUserName;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
    private String replyText;
    private LocalDateTime replyAt;
    private LocalDateTime replyUpdatedAt;
    private LocalDateTime editedAt;
    private Integer editCount;
    /** Last moment the author may still edit this review. */
    private LocalDateTime editableUntil;
    /** Last moment the recipient may still edit the reply, null when no reply exists. */
    private LocalDateTime replyEditableUntil;
}
