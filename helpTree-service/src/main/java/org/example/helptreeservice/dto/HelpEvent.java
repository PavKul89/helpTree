package org.example.helptreeservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private Long receiverId; // ID получателя помощи (для rating-service)
    private String eventType; // HELP_RESPONSE, HELP_ACCEPTED, HELP_COMPLETED, HELP_CONFIRMED, CANCELLED
    private LocalDateTime timestamp;
    private Long duration; // время между принятием и завершением в минутах
}
