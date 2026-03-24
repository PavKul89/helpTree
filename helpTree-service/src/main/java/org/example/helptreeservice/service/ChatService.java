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
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.ChatMapper;
import org.example.helptreeservice.repository.ChatRepository;
import org.example.helptreeservice.repository.MessageRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    @Transactional
    public ChatResponse createChat(CreateChatRequest request, Long currentUserId) {
        if (request.getParticipantId().equals(currentUserId)) {
            throw new BadRequestException("Нельзя создать чат с самим собой");
        }

        User participant = userRepository.findById(request.getParticipantId())
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        Chat chat = chatRepository.findByUsers(currentUserId, request.getParticipantId())
                .orElseGet(() -> {
                    User currentUser = userRepository.findById(currentUserId)
                            .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
                    
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
        List<Chat> chats = chatRepository.findByUserId(currentUserId);
        return chats.stream()
                .map(chat -> chatMapper.toResponse(chat, currentUserId))
                .collect(Collectors.toList());
    }

    @Transactional
    public MessageResponse sendMessage(Long chatId, CreateMessageRequest request, Long currentUserId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new NotFoundException("Чат не найден"));

        if (!chat.getUser1().getId().equals(currentUserId) && !chat.getUser2().getId().equals(currentUserId)) {
            throw new BadRequestException("Вы не участник этого чата");
        }

        User sender = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

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
        return chatMapper.toMessageResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(Long chatId, Long currentUserId, Pageable pageable) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new NotFoundException("Чат не найден"));

        if (!chat.getUser1().getId().equals(currentUserId) && !chat.getUser2().getId().equals(currentUserId)) {
            throw new BadRequestException("Вы не участник этого чата");
        }

        return messageRepository.findByChatIdOrderByCreatedAtDesc(chatId, pageable)
                .map(chatMapper::toMessageResponse);
    }

    @Transactional
    public void markMessagesAsRead(Long chatId, Long currentUserId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new NotFoundException("Чат не найден"));

        if (!chat.getUser1().getId().equals(currentUserId) && !chat.getUser2().getId().equals(currentUserId)) {
            throw new BadRequestException("Вы не участник этого чата");
        }

        messageRepository.markMessagesAsRead(chatId, currentUserId);
        log.info("Пользователь {} прочитал сообщения в чате {}", currentUserId, chatId);
    }

    @Transactional
    public void deleteChat(Long chatId, Long currentUserId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new NotFoundException("Чат не найден"));

        if (!chat.getUser1().getId().equals(currentUserId) && !chat.getUser2().getId().equals(currentUserId)) {
            throw new BadRequestException("Вы не участник этого чата");
        }

        chatRepository.delete(chat);
        log.info("Пользователь {} удалил чат {}", currentUserId, chatId);
    }

    @Transactional
    public void deleteMessage(Long chatId, Long messageId, Long currentUserId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new NotFoundException("Чат не найден"));

        if (!chat.getUser1().getId().equals(currentUserId) && !chat.getUser2().getId().equals(currentUserId)) {
            throw new BadRequestException("Вы не участник этого чата");
        }

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundException("Сообщение не найден"));

        if (!message.getSender().getId().equals(currentUserId)) {
            throw new BadRequestException("Вы можете удалить только свое сообщение");
        }

        messageRepository.delete(message);
        log.info("Пользователь {} удалил сообщение {} из чата {}", currentUserId, messageId, chatId);
    }
}
