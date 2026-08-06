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
public class WsMessageEnvelope {

    private String type;
    private MessageResponse message;
    private ChatResponse chat;
    private Long chatId;
    private LocalDateTime timestamp;

    public static WsMessageEnvelope newMessage(MessageResponse message) {
        return WsMessageEnvelope.builder()
                .type("NEW_MESSAGE")
                .message(message)
                .chatId(message.getChatId())
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static WsMessageEnvelope messageDeleted(Long chatId, Long messageId) {
        return WsMessageEnvelope.builder()
                .type("MESSAGE_DELETED")
                .chatId(chatId)
                .message(MessageResponse.builder().id(messageId).chatId(chatId).build())
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static WsMessageEnvelope chatDeleted(Long chatId) {
        return WsMessageEnvelope.builder()
                .type("CHAT_DELETED")
                .chatId(chatId)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static WsMessageEnvelope messagesRead(Long chatId) {
        return WsMessageEnvelope.builder()
                .type("MESSAGES_READ")
                .chatId(chatId)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static WsMessageEnvelope chatUpdated(ChatResponse chat) {
        return WsMessageEnvelope.builder()
                .type("CHAT_UPDATED")
                .chat(chat)
                .chatId(chat.getId())
                .timestamp(LocalDateTime.now())
                .build();
    }
}
