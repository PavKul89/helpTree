package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.chat.ChatResponse;
import org.example.helptreeservice.dto.chat.CreateChatRequest;
import org.example.helptreeservice.dto.chat.CreateMessageRequest;
import org.example.helptreeservice.dto.chat.MessageResponse;
import org.example.helptreeservice.entity.Chat;
import org.example.helptreeservice.entity.Message;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.ChatMapper;
import org.example.helptreeservice.repository.ChatRepository;
import org.example.helptreeservice.repository.MessageRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.dto.chat.WsMessageEnvelope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatMapper chatMapper;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public ChatResponse createChat(CreateChatRequest request, Long currentUserId) {
        if (request.getParticipantId().equals(currentUserId)) {
            throw new BadRequestException("Нельзя создать чат с самим собой");
        }

        User participant = getActiveUser(request.getParticipantId());

        Chat chat = chatRepository.findByUsers(currentUserId, request.getParticipantId())
                .orElseGet(() -> {
                    User currentUser = getActiveUser(currentUserId);
                    
                    Chat newChat = Chat.builder()
                            .user1(currentUser)
                            .user2(participant)
                            .build();
                    return chatRepository.save(newChat);
                });

        log.info("Создан/найден чат ID={} между пользователями {} и {}", chat.getId(), currentUserId, request.getParticipantId());
        return chatMapper.toResponse(chat, currentUserId);
    }

    @Transactional(readOnly = true)
    public List<ChatResponse> getChats(Long currentUserId) {
        getActiveUser(currentUserId);
        List<Chat> chats = chatRepository.findByUserId(currentUserId);
        return chats.stream()
                .map(chat -> chatMapper.toResponse(chat, currentUserId))
                .collect(Collectors.toList());
    }

    @Transactional
    public MessageResponse sendMessage(Long chatId, CreateMessageRequest request, Long currentUserId) {
        Chat chat = getChatForParticipant(chatId, currentUserId);
        User sender = getActiveUser(currentUserId);

        Message message = Message.builder()
                .chat(chat)
                .sender(sender)
                .content(request.getContent())
                .isRead(false)
                .build();

        Message saved = messageRepository.save(message);

        chat.setLastMessageAt(LocalDateTime.now());
        chatRepository.save(chat);

        log.info("Пользователь {} отправил сообщение в чат {}", currentUserId, chatId);
        MessageResponse response = chatMapper.toMessageResponse(saved);

        Long otherUserId = chat.getUser1().getId().equals(currentUserId)
                ? chat.getUser2().getId()
                : chat.getUser1().getId();

        ChatResponse chatResponse = chatMapper.toResponse(chat, currentUserId);
        ChatResponse otherChatResponse = chatMapper.toResponse(chat, otherUserId);

        messagingTemplate.convertAndSend("/topic/chat/" + chatId, WsMessageEnvelope.newMessage(response));
        messagingTemplate.convertAndSend("/topic/user/" + currentUserId, WsMessageEnvelope.chatUpdated(chatResponse));
        messagingTemplate.convertAndSend("/topic/user/" + otherUserId, WsMessageEnvelope.chatUpdated(otherChatResponse));

        return response;
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(Long chatId, Long currentUserId, Pageable pageable) {
        getChatForParticipant(chatId, currentUserId);

        return messageRepository.findByChatIdOrderByCreatedAtDesc(chatId, pageable)
                .map(chatMapper::toMessageResponse);
    }

    @Transactional
    public void markMessagesAsRead(Long chatId, Long currentUserId) {
        getChatForParticipant(chatId, currentUserId);

        messageRepository.markMessagesAsRead(chatId, currentUserId);
        log.info("Пользователь {} прочитал сообщения в чате {}", currentUserId, chatId);

        messagingTemplate.convertAndSend("/topic/chat/" + chatId, WsMessageEnvelope.messagesRead(chatId));
    }

    @Transactional
    public void deleteChat(Long chatId, Long currentUserId) {
        Chat chat = getChatForParticipant(chatId, currentUserId);

        Long otherUserId = chat.getUser1().getId().equals(currentUserId) 
                ? chat.getUser2().getId() 
                : chat.getUser1().getId();

        chatRepository.delete(chat);
        log.info("Пользователь {} удалил чат {}", currentUserId, chatId);

        messagingTemplate.convertAndSend("/topic/user/" + currentUserId, WsMessageEnvelope.chatDeleted(chatId));
        messagingTemplate.convertAndSend("/topic/user/" + otherUserId, WsMessageEnvelope.chatDeleted(chatId));
    }

    @Transactional
    public void deleteMessage(Long chatId, Long messageId, Long currentUserId) {
        getChatForParticipant(chatId, currentUserId);

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Сообщение не найдено"));

        if (message.getChat() == null || !message.getChat().getId().equals(chatId)) {
            throw new NotFoundException("Сообщение не найдено");
        }

        if (!message.getSender().getId().equals(currentUserId)) {
            throw new ForbiddenException("Вы можете удалить только свое сообщение");
        }

        messageRepository.delete(message);
        log.info("Пользователь {} удалил сообщение {} из чата {}", currentUserId, messageId, chatId);

        messagingTemplate.convertAndSend("/topic/chat/" + chatId, WsMessageEnvelope.messageDeleted(chatId, messageId));
    }

    private Chat getChatForParticipant(Long chatId, Long currentUserId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new NotFoundException("Чат не найден"));

        if (!chat.getUser1().getId().equals(currentUserId) && !chat.getUser2().getId().equals(currentUserId)) {
            throw new ForbiddenException("Вы не участник этого чата");
        }

        return chat;
    }

    private User getActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new NotFoundException("Пользователь не найден");
        }

        return user;
    }
}
