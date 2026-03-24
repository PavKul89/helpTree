package org.example.helptreeservice.mapper;

import org.example.helptreeservice.dto.chat.ChatResponse;
import org.example.helptreeservice.dto.chat.MessageResponse;
import org.example.helptreeservice.entity.Chat;
import org.example.helptreeservice.entity.Message;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.repository.MessageRepository;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ChatMapper {

    private final MessageRepository messageRepository;

    public ChatMapper(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public ChatResponse toResponse(Chat chat, Long currentUserId) {
        User participant = chat.getUser1().getId().equals(currentUserId) 
                ? chat.getUser2() 
                : chat.getUser1();

        String lastMessage = null;
        List<Message> messages = messageRepository.findByChatIdOrderByCreatedAtDesc(
                chat.getId(), 
                org.springframework.data.domain.PageRequest.of(0, 1)
        ).getContent();
        
        if (!messages.isEmpty()) {
            lastMessage = messages.get(0).getContent();
            if (lastMessage != null && lastMessage.length() > 50) {
                lastMessage = lastMessage.substring(0, 50) + "...";
            }
        }

        long unreadCount = messageRepository.countByChatIdAndIsReadFalseAndSenderIdNot(chat.getId(), currentUserId);

        return ChatResponse.builder()
                .id(chat.getId())
                .participantId(participant.getId())
                .participantName(participant.getName())
                .lastMessage(lastMessage)
                .lastMessageAt(chat.getLastMessageAt())
                .unreadCount(unreadCount)
                .build();
    }

    public MessageResponse toMessageResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId())
                .chatId(message.getChat().getId())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getName())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .isRead(message.getIsRead())
                .build();
    }
}
