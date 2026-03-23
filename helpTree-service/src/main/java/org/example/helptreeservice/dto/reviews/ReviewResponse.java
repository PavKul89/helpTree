package org.example.helptreeservice.dto.reviews;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ReviewResponse {
    private Long id;
    private Long helpId;
    private Long fromUserId;
    private String fromUserName;
    private Long toUserId;
    private String toUserName;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}
