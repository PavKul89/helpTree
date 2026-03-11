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
    private Long helperId;
    private Long receiverId;
    private String eventType; // ACCEPTED, COMPLETED, CONFIRMED, CANCELLED
    private LocalDateTime timestamp;
    private Long duration; // время между принятием и завершением в минутах
}
