package com.example.helpTree.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class HelpResponse {
    private Long id;
    private Long postId;
    private String postTitle;
    private Long helperId;
    private String helperName;
    private Long receiverId;
    private String receiverName;
    private String status;
    private LocalDateTime acceptedAt;
    private LocalDateTime completedAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime createdAt;
}
