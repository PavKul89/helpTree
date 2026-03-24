package org.example.helptreeservice.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatResponse {

    private Long id;
    private Long participantId;
    private String participantName;
    private String lastMessage;
    private LocalDateTime lastMessageAt;
    private Long unreadCount;
}
