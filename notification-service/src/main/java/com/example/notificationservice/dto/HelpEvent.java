package com.example.notificationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class HelpEvent {
    private Long helpId;
    private Long postId;
    private String postTitle;
    private Long authorId;
    private String authorEmail;
    private String authorName;
    private Long helperId;
    private String helperEmail;
    private String helperName;
    private String eventType;
    private LocalDateTime timestamp;
    private Long duration;
}
