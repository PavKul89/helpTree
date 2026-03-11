package org.example.ratingservice.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    private Long duration; // Время между принятием и завершением в минутах
}
