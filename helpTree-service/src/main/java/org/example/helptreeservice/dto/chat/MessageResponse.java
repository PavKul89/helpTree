package org.example.helptreeservice.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse {

    private Long id;
    private Long chatId;
    private Long senderId;
    private String senderName;
    private String senderAvatarUrl;
    private String content;
    private LocalDateTime createdAt;
    private Boolean isRead;
    private List<String> attachmentUrls;
    private LocalDateTime editedAt;
    private Integer editCount;
    /** Last moment the sender may still edit this message. */
    private LocalDateTime editableUntil;
}
