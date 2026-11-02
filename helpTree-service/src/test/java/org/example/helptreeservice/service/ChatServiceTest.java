package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.chat.ChatResponse;
import org.example.helptreeservice.dto.chat.CreateMessageRequest;
import org.example.helptreeservice.entity.Chat;
import org.example.helptreeservice.entity.Message;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.ChatMapper;
import org.example.helptreeservice.repository.ChatRepository;
import org.example.helptreeservice.repository.MessageRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatRepository chatRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChatMapper chatMapper;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatService chatService;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        user1 = user(1L, "Alice", false);
        user2 = user(2L, "Bob", false);
    }

    private User user(Long id, String name, boolean deleted) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setDeleted(deleted);
        return user;
    }

    private Chat chat() {
        return Chat.builder().id(10L).user1(user1).user2(user2).build();
    }

    @Test
    void deleteMessage_shouldRejectMessageFromAnotherChat() {
        Chat chat = chat();
        Message foreignMessage = Message.builder()
                .id(99L)
                .chat(Chat.builder().id(11L).user1(user1).user2(user2).build())
                .sender(user1)
                .content("secret")
                .build();

        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(messageRepository.findById(99L)).thenReturn(Optional.of(foreignMessage));

        assertThrows(NotFoundException.class, () -> chatService.deleteMessage(10L, 99L, 1L));
        verify(messageRepository, never()).delete(any());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void deleteMessage_shouldForbidDeletingSomeoneElsesMessage() {
        Chat chat = chat();
        Message message = Message.builder()
                .id(99L)
                .chat(chat)
                .sender(user2)
                .content("Bob's message")
                .build();

        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(messageRepository.findById(99L)).thenReturn(Optional.of(message));

        assertThrows(ForbiddenException.class, () -> chatService.deleteMessage(10L, 99L, 1L));
        verify(messageRepository, never()).delete(any());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void deleteMessage_shouldDeleteOwnMessageOfParticipant() {
        Chat chat = chat();
        Message message = Message.builder()
                .id(99L)
                .chat(chat)
                .sender(user1)
                .content("Alice's message")
                .build();

        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(messageRepository.findById(99L)).thenReturn(Optional.of(message));

        chatService.deleteMessage(10L, 99L, 1L);

        verify(messageRepository).delete(message);
        verify(messagingTemplate).convertAndSend(eq("/topic/chat/10"), any(Object.class));
    }

    @Test
    void getMessages_shouldRejectNonParticipant() {
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat()));

        assertThrows(ForbiddenException.class,
                () -> chatService.getMessages(10L, 3L, PageRequest.of(0, 20)));
        verify(messageRepository, never()).findByChatIdOrderByCreatedAtDesc(anyLong(), any());
    }

    @Test
    void sendMessage_shouldRejectDeletedSender() {
        Chat chat = chat();
        User deleted = user(3L, "Deleted", true);
        when(chatRepository.findById(10L)).thenReturn(Optional.of(Chat.builder()
                .id(10L).user1(user1).user2(deleted).build()));
        when(userRepository.findById(3L)).thenReturn(Optional.of(deleted));
        CreateMessageRequest request = new CreateMessageRequest();
        request.setContent("hello");

        assertThrows(NotFoundException.class, () -> chatService.sendMessage(10L, request, 3L));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void getChats_shouldRejectDeletedUser() {
        User deleted = user(3L, "Deleted", true);
        when(userRepository.findById(3L)).thenReturn(Optional.of(deleted));

        assertThrows(NotFoundException.class, () -> chatService.getChats(3L));
        verify(chatRepository, never()).findByUserId(anyLong());
    }

    @Test
    void createChat_shouldRejectChatWithSelf() {
        CreateMessageRequest ignored = new CreateMessageRequest();
        org.example.helptreeservice.dto.chat.CreateChatRequest request =
                new org.example.helptreeservice.dto.chat.CreateChatRequest();
        request.setParticipantId(1L);

        assertThrows(org.example.helptreeservice.exception.BadRequestException.class,
                () -> chatService.createChat(request, 1L));
        verifyNoInteractions(chatRepository, userRepository, chatMapper, messagingTemplate);
    }

    @Test
    void getChats_shouldReturnMappedChatsForActiveUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        Chat chat = chat();
        when(chatRepository.findByUserId(1L)).thenReturn(List.of(chat));
        when(chatMapper.toResponse(chat, 1L)).thenReturn(ChatResponse.builder().id(10L).build());

        List<ChatResponse> result = chatService.getChats(1L);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getId());
    }
}
