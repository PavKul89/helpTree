package org.example.helptreeservice.service;

import org.example.helptreeservice.config.ChatProperties;
import org.example.helptreeservice.dto.chat.ChatResponse;
import org.example.helptreeservice.dto.chat.CreateChatRequest;
import org.example.helptreeservice.dto.chat.CreateMessageRequest;
import org.example.helptreeservice.dto.chat.MessageCursorResponse;
import org.example.helptreeservice.dto.chat.MessageResponse;
import org.example.helptreeservice.dto.chat.UpdateMessageRequest;
import org.example.helptreeservice.entity.Chat;
import org.example.helptreeservice.entity.ChatUserState;
import org.example.helptreeservice.entity.Message;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.UserBlockScope;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.exception.RateLimitExceededException;
import org.example.helptreeservice.mapper.ChatMapper;
import org.example.helptreeservice.repository.ChatRepository;
import org.example.helptreeservice.repository.ChatUserStateRepository;
import org.example.helptreeservice.repository.MessageRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatRepository chatRepository;
    @Mock
    private ChatUserStateRepository chatUserStateRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChatMapper chatMapper;
    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private ChatRateLimiter chatRateLimiter;
    @Mock
    private UserBlockService userBlockService;
    @Mock
    private ImageObjectService imageObjectService;

    @Spy
    private ChatProperties chatProperties = new ChatProperties();
    @Spy
    private ChatContentSanitizer contentSanitizer =
            new ChatContentSanitizer(new ChatProperties());

    @InjectMocks
    private ChatService chatService;

    private User user1;
    private User user2;

    @BeforeEach
    void setUp() {
        user1 = user(1L, "Alice");
        user2 = user(2L, "Bob");
    }

    private User user(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setDeleted(false);
        return user;
    }

    private Chat chat() {
        return Chat.builder().id(10L).user1(user1).user2(user2).build();
    }

    private ChatUserState state(Chat chat, User user) {
        return ChatUserState.builder().id(100L + user.getId()).chat(chat).user(user).build();
    }

    private void stubParticipantChat() {
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat()));
    }

    private void stubActiveUsers() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user2));
    }

    private Message message(Chat chat, User sender, Long id, String content) {
        return Message.builder()
                .id(id)
                .chat(chat)
                .sender(sender)
                .content(content)
                .createdAt(LocalDateTime.now())
                .isRead(false)
                .build();
    }

    private MessageResponse messageResponse(Long id) {
        return MessageResponse.builder().id(id).chatId(10L).content("hello").build();
    }

    // ---------- createChat ----------

    @Test
    void createChat_shouldRejectChatWithSelf() {
        CreateChatRequest request = new CreateChatRequest();
        request.setParticipantId(1L);

        assertThrows(BadRequestException.class, () -> chatService.createChat(request, 1L));
        verifyNoInteractions(chatRepository, userRepository, chatMapper, messagingTemplate);
    }

    @Test
    void createChat_shouldRejectMissingParticipant() {
        assertThrows(BadRequestException.class,
                () -> chatService.createChat(new CreateChatRequest(), 1L));
        verifyNoInteractions(chatRepository, userRepository);
    }

    @Test
    void createChat_shouldLockBothUsersInDeterministicOrderAndCreateCanonicalChat() {
        User user3 = user(3L, "Carol");
        when(userRepository.findAllByIdInForUpdate(List.of(1L, 3L)))
                .thenReturn(List.of(user3, user1));

        Chat saved = Chat.builder().id(10L).user1(user1).user2(user3).build();
        when(chatRepository.findByUsers(1L, 3L)).thenReturn(Optional.empty());
        when(chatRepository.save(any(Chat.class))).thenReturn(saved);
        ChatUserState currentState = ChatUserState.builder()
                .id(1L).chat(saved).user(user1).build();
        when(chatUserStateRepository.findByChatIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(currentState));
        when(chatUserStateRepository.findByChatIdAndUserId(10L, 3L))
                .thenReturn(Optional.of(ChatUserState.builder().chat(saved).user(user3).build()));
        when(chatRepository.findById(10L)).thenReturn(Optional.of(saved));
        ChatResponse response = ChatResponse.builder().id(10L).participantId(3L).build();
        when(chatMapper.toResponse(saved, 1L, currentState)).thenReturn(response);

        CreateChatRequest request = new CreateChatRequest();
        request.setParticipantId(3L);

        ChatResponse result = chatService.createChat(request, 1L);

        assertEquals(10L, result.getId());
        verify(userRepository).findAllByIdInForUpdate(List.of(1L, 3L));
        verify(chatUserStateRepository).restore(10L, 1L);
        verify(chatMapper).toResponse(saved, 1L, currentState);
    }

    @Test
    void createChat_shouldRejectBlockedParticipant() {
        User blocked = user(3L, "Blocked");
        blocked.setBlockedUntil(LocalDateTime.now().plusDays(1));
        when(userRepository.findAllByIdInForUpdate(List.of(1L, 3L)))
                .thenReturn(List.of(user1, blocked));

        CreateChatRequest request = new CreateChatRequest();
        request.setParticipantId(3L);

        assertThrows(ForbiddenException.class, () -> chatService.createChat(request, 1L));
        verify(chatRepository, never()).save(any());
    }

    @Test
    void createChat_shouldRejectUserBlock() {
        when(userRepository.findAllByIdInForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(user1, user2));
        doThrow(new ForbiddenException("blocked")).when(userBlockService)
                .assertInteractionAllowed(anyLong(), anyLong(),
                        eq(UserBlockScope.MESSAGES), anyString());

        CreateChatRequest request = new CreateChatRequest();
        request.setParticipantId(2L);

        assertThrows(ForbiddenException.class, () -> chatService.createChat(request, 1L));
        verify(userBlockService).assertInteractionAllowed(1L, 2L, UserBlockScope.MESSAGES,
                "Один из пользователей ограничил переписку");
        verify(chatRepository, never()).save(any());
    }

    @Test
    void createChat_shouldReturnExistingChatAndRestoreVisibility() {
        Chat chat = chat();
        when(userRepository.findAllByIdInForUpdate(List.of(1L, 2L)))
                .thenReturn(List.of(user1, user2));
        when(chatRepository.findByUsers(1L, 2L)).thenReturn(Optional.of(chat));
        when(chatUserStateRepository.findByChatIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(state(chat, user1)));
        when(chatUserStateRepository.findByChatIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(state(chat, user2)));
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(chatMapper.toResponse(eq(chat), eq(1L), any()))
                .thenReturn(ChatResponse.builder().id(10L).build());

        CreateChatRequest request = new CreateChatRequest();
        request.setParticipantId(2L);

        chatService.createChat(request, 1L);

        verify(chatRepository, never()).save(any(Chat.class));
        verify(chatUserStateRepository).restore(10L, 1L);
    }

    // ---------- getChats ----------

    @Test
    void getChats_shouldRejectDeletedUser() {
        User deleted = user(3L, "Deleted");
        deleted.setDeleted(true);
        when(userRepository.findById(3L)).thenReturn(Optional.of(deleted));

        assertThrows(NotFoundException.class, () -> chatService.getChats(3L));
        verify(chatRepository, never()).findVisibleByUserId(anyLong());
    }

    @Test
    void getChats_shouldReturnVisibleChatsWithOwnUnreadState() {
        Chat chat = chat();
        ChatUserState state = state(chat, user1);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(chatRepository.findVisibleByUserId(1L)).thenReturn(List.of(chat));
        when(chatUserStateRepository.findByUserIdAndDeletedAtIsNull(1L))
                .thenReturn(List.of(state));
        when(chatMapper.toResponse(chat, 1L, state))
                .thenReturn(ChatResponse.builder().id(10L).build());

        List<ChatResponse> result = chatService.getChats(1L);

        assertEquals(1, result.size());
        assertEquals(10L, result.get(0).getId());
    }

    // ---------- sendMessage ----------

    @Test
    void sendMessage_shouldRejectNonParticipant() {
        stubParticipantChat();

        assertThrows(ForbiddenException.class, () -> chatService.sendMessage(
                10L, new CreateMessageRequest("hello", null), 3L));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_shouldRejectBlockedRecipient() {
        Chat chat = chat();
        user2.setBlockedUntil(LocalDateTime.now().plusHours(1));
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        stubActiveUsers();

        assertThrows(ForbiddenException.class, () -> chatService.sendMessage(
                10L, new CreateMessageRequest("hello", null), 1L));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_shouldRejectUserBlock() {
        Chat chat = chat();
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        stubActiveUsers();
        doThrow(new ForbiddenException("blocked")).when(userBlockService)
                .assertInteractionAllowed(anyLong(), anyLong(),
                        eq(UserBlockScope.MESSAGES), anyString());

        assertThrows(ForbiddenException.class, () -> chatService.sendMessage(
                10L, new CreateMessageRequest("hello", null), 1L));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_shouldRejectBlankContent() {
        stubParticipantChat();
        stubActiveUsers();

        assertThrows(BadRequestException.class, () -> chatService.sendMessage(
                10L, new CreateMessageRequest("  \n\t ", null), 1L));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_shouldRejectTooLongContent() {
        stubParticipantChat();
        stubActiveUsers();

        assertThrows(BadRequestException.class, () -> chatService.sendMessage(
                10L, new CreateMessageRequest("a".repeat(5001), null), 1L));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_shouldRejectRateLimitedUser() {
        stubParticipantChat();
        stubActiveUsers();
        doThrow(new RateLimitExceededException("slow down", 60))
                .when(chatRateLimiter).checkAllowed(1L);

        assertThrows(RateLimitExceededException.class, () -> chatService.sendMessage(
                10L, new CreateMessageRequest("hello", null), 1L));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_shouldSanitizeStoreAndPublishOnlyToParticipants() {
        Chat chat = chat();
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        stubActiveUsers();
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        MessageResponse response = messageResponse(99L);
        when(chatMapper.toMessageResponse(any(Message.class))).thenReturn(response);
        ChatUserState state1 = state(chat, user1);
        ChatUserState state2 = state(chat, user2);
        when(chatUserStateRepository.findByChatIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(state1));
        when(chatUserStateRepository.findByChatIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(state2));
        when(chatMapper.toResponse(eq(chat), eq(1L), any()))
                .thenReturn(ChatResponse.builder().id(10L).participantId(2L).build());
        when(chatMapper.toResponse(eq(chat), eq(2L), any()))
                .thenReturn(ChatResponse.builder().id(10L).participantId(1L).build());

        MessageResponse result = chatService.sendMessage(
                10L, new CreateMessageRequest("  hello  ", null), 1L);

        assertEquals(response, result);
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        assertEquals("hello", captor.getValue().getContent());
        assertEquals(user1, captor.getValue().getSender());
        assertFalse(captor.getValue().getIsRead());

        verify(chatRepository).save(chat);
        verify(chatUserStateRepository).restore(10L, 1L);
        verify(chatUserStateRepository).restore(10L, 2L);
        verify(chatUserStateRepository).markReadUpTo(10L, 1L, 99L);
        verify(messagingTemplate)
                .convertAndSendToUser(eq("1"), eq("/queue/chat/10"), any());
        verify(messagingTemplate)
                .convertAndSendToUser(eq("2"), eq("/queue/chat/10"), any());
        verify(messagingTemplate)
                .convertAndSendToUser(eq("1"), eq("/queue/chat-updates"), any());
        verify(messagingTemplate)
                .convertAndSendToUser(eq("2"), eq("/queue/chat-updates"), any());
        verify(messagingTemplate).convertAndSend(eq("/topic/chat/10"), any(Object.class));
    }

    @Test
    void sendMessage_shouldDeferWebSocketDeliveryUntilCommit() {
        stubSuccessfulSend();

        TransactionSynchronizationManager.initSynchronization();
        try {
            chatService.sendMessage(10L, new CreateMessageRequest("hello", null), 1L);

            verifyNoInteractions(messagingTemplate);

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verify(messagingTemplate)
                .convertAndSendToUser(eq("1"), eq("/queue/chat/10"), any());
        verify(messagingTemplate)
                .convertAndSendToUser(eq("2"), eq("/queue/chat/10"), any());
        verify(messagingTemplate).convertAndSend(eq("/topic/chat/10"), any(Object.class));
    }

    @Test
    void sendMessage_shouldNotDeliverWebSocketEventsWhenTransactionRollsBack() {
        stubSuccessfulSend();

        TransactionSynchronizationManager.initSynchronization();
        try {
            chatService.sendMessage(10L, new CreateMessageRequest("hello", null), 1L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void sendMessage_shouldStoreClaimedAttachments() {
        Chat chat = chat();
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        stubActiveUsers();
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        when(chatMapper.toMessageResponse(any(Message.class))).thenReturn(messageResponse(99L));
        when(chatUserStateRepository.findByChatIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(state(chat, user1)));
        when(chatUserStateRepository.findByChatIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(state(chat, user2)));
        when(chatMapper.toResponse(eq(chat), eq(1L), any()))
                .thenReturn(ChatResponse.builder().id(10L).build());
        when(chatMapper.toResponse(eq(chat), eq(2L), any()))
                .thenReturn(ChatResponse.builder().id(10L).build());

        chatService.sendMessage(10L,
                new CreateMessageRequest("", List.of("u1", "u2")), 1L);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        assertEquals(List.of("u1", "u2"), captor.getValue().getAttachmentUrls());
        verify(imageObjectService).claimForOwner(1L, List.of("u1", "u2"),
                org.example.helptreeservice.enums.ImagePurpose.CHAT);
    }

    @Test
    void sendMessage_shouldRejectTooManyAttachments() {
        stubParticipantChat();
        stubActiveUsers();

        assertThrows(BadRequestException.class, () -> chatService.sendMessage(10L,
                new CreateMessageRequest("hello", List.of("u1", "u2", "u3", "u4")), 1L));

        verify(imageObjectService, never()).claimForOwner(anyLong(), any(), any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void sendMessage_shouldAllowAttachmentOnlyMessage() {
        Chat chat = chat();
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        stubActiveUsers();
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        when(chatMapper.toMessageResponse(any(Message.class))).thenReturn(messageResponse(99L));
        when(chatUserStateRepository.findByChatIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(state(chat, user1)));
        when(chatUserStateRepository.findByChatIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(state(chat, user2)));
        when(chatMapper.toResponse(eq(chat), eq(1L), any()))
                .thenReturn(ChatResponse.builder().id(10L).build());
        when(chatMapper.toResponse(eq(chat), eq(2L), any()))
                .thenReturn(ChatResponse.builder().id(10L).build());

        chatService.sendMessage(10L,
                new CreateMessageRequest(null, List.of("u1")), 1L);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        assertEquals("", captor.getValue().getContent());
        assertEquals(List.of("u1"), captor.getValue().getAttachmentUrls());
    }

    // ---------- message editing ----------

    @Test
    void editMessage_shouldRejectAnotherUsersMessage() {
        Chat chat = chat();
        Message message = message(chat, user2, 99L, "Bob's message");
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(messageRepository.findByIdAndChatIdAndDeletedAtIsNull(99L, 10L))
                .thenReturn(Optional.of(message));

        assertThrows(ForbiddenException.class,
                () -> chatService.editMessage(10L, 99L, updateRequest("hacked"), 1L));
        verify(messageRepository, never()).save(any());
    }

    @Test
    void editMessage_shouldRejectAfterWindow() {
        Chat chat = chat();
        Message message = message(chat, user1, 99L, "old");
        message.setCreatedAt(LocalDateTime.now().minusMinutes(16));
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(messageRepository.findByIdAndChatIdAndDeletedAtIsNull(99L, 10L))
                .thenReturn(Optional.of(message));

        assertThrows(BadRequestException.class,
                () -> chatService.editMessage(10L, 99L, updateRequest("new"), 1L));
        assertEquals("old", message.getContent());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void editMessage_shouldUpdateContentAndKeepAttachments() {
        Chat chat = chat();
        Message message = message(chat, user1, 99L, "old");
        message.setAttachmentUrls(new java.util.ArrayList<>(List.of("u1")));
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(messageRepository.findByIdAndChatIdAndDeletedAtIsNull(99L, 10L))
                .thenReturn(Optional.of(message));
        when(messageRepository.save(message)).thenReturn(message);
        when(chatMapper.toMessageResponse(message)).thenReturn(messageResponse(99L));
        when(chatMapper.toResponse(eq(chat), eq(1L), any()))
                .thenReturn(ChatResponse.builder().id(10L).build());
        when(chatMapper.toResponse(eq(chat), eq(2L), any()))
                .thenReturn(ChatResponse.builder().id(10L).build());

        chatService.editMessage(10L, 99L, updateRequest(" new "), 1L);

        assertEquals("new", message.getContent());
        assertNotNull(message.getEditedAt());
        assertEquals(1, message.getEditCount());
        assertEquals(List.of("u1"), message.getAttachmentUrls());
        verify(messageRepository).save(message);
    }

    @Test
    void editMessage_shouldAllowClearingTextWhenAttachmentsRemain() {
        Chat chat = chat();
        Message message = message(chat, user1, 99L, "old");
        message.setAttachmentUrls(new java.util.ArrayList<>(List.of("u1")));
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(messageRepository.findByIdAndChatIdAndDeletedAtIsNull(99L, 10L))
                .thenReturn(Optional.of(message));
        when(messageRepository.save(message)).thenReturn(message);
        when(chatMapper.toMessageResponse(message)).thenReturn(messageResponse(99L));
        when(chatMapper.toResponse(eq(chat), eq(1L), any()))
                .thenReturn(ChatResponse.builder().id(10L).build());
        when(chatMapper.toResponse(eq(chat), eq(2L), any()))
                .thenReturn(ChatResponse.builder().id(10L).build());

        chatService.editMessage(10L, 99L, updateRequest("   "), 1L);

        assertEquals("", message.getContent());
        verify(messageRepository).save(message);
    }

    @Test
    void editMessage_shouldRejectEmptyTextWithoutAttachments() {
        Chat chat = chat();
        Message message = message(chat, user1, 99L, "old");
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(messageRepository.findByIdAndChatIdAndDeletedAtIsNull(99L, 10L))
                .thenReturn(Optional.of(message));

        assertThrows(BadRequestException.class,
                () -> chatService.editMessage(10L, 99L, updateRequest("   "), 1L));
        verify(messageRepository, never()).save(any());
    }

    // ---------- retention ----------

    @Test
    void purgeExpiredMessages_shouldDeleteBatchClearCursorsAndReleaseAttachments() {
        Chat chat = chat();
        Message expired = Message.builder()
                .id(99L)
                .chat(chat)
                .sender(user1)
                .content("old")
                .createdAt(LocalDateTime.now().minusDays(91))
                .attachmentUrls(new java.util.ArrayList<>(List.of("u1")))
                .build();
        when(messageRepository.findExpiredMessages(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(expired));
        when(messageRepository.deleteByIdIn(List.of(99L))).thenReturn(1);
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(messageRepository.findTopByChatIdAndDeletedAtIsNullOrderByIdDesc(10L))
                .thenReturn(Optional.empty());
        when(messageRepository.existsActiveByAttachmentUrl("u1")).thenReturn(false);

        int deleted = chatService.purgeExpiredMessages(
                LocalDateTime.now().minusDays(90), 100);

        assertEquals(1, deleted);
        verify(chatUserStateRepository).clearLastReadMessageIds(List.of(99L));
        verify(imageObjectService).markChatAttachmentsDeleted(List.of("u1"));
        assertNull(chat.getLastMessageAt());
    }

    @Test
    void purgeExpiredMessages_shouldDoNothingWhenNoExpiredMessages() {
        when(messageRepository.findExpiredMessages(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());

        int deleted = chatService.purgeExpiredMessages(
                LocalDateTime.now().minusDays(90), 100);

        assertEquals(0, deleted);
        verify(messageRepository, never()).deleteByIdIn(any());
        verifyNoInteractions(imageObjectService);
    }

    private UpdateMessageRequest updateRequest(String text) {
        UpdateMessageRequest request = new UpdateMessageRequest();
        request.setContent(text);
        return request;
    }

    private void stubSuccessfulSend() {
        Chat chat = chat();
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        stubActiveUsers();
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        when(chatMapper.toMessageResponse(any(Message.class)))
                .thenReturn(messageResponse(99L));
        when(chatUserStateRepository.findByChatIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(state(chat, user1)));
        when(chatUserStateRepository.findByChatIdAndUserId(10L, 2L))
                .thenReturn(Optional.of(state(chat, user2)));
        when(chatMapper.toResponse(eq(chat), eq(1L), any()))
                .thenReturn(ChatResponse.builder().id(10L).participantId(2L).build());
        when(chatMapper.toResponse(eq(chat), eq(2L), any()))
                .thenReturn(ChatResponse.builder().id(10L).participantId(1L).build());
    }

    // ---------- message history ----------

    @Test
    void getMessages_shouldRejectNonParticipant() {
        stubParticipantChat();

        assertThrows(ForbiddenException.class,
                () -> chatService.getMessages(10L, 3L, PageRequest.of(0, 20)));
        verify(messageRepository, never())
                .findByChatIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(anyLong(), any());
    }

    @Test
    void getMessages_shouldReturnActiveMessagesOnly() {
        Chat chat = chat();
        Message message = message(chat, user1, 99L, "hello");
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(messageRepository.findByChatIdAndDeletedAtIsNullOrderByCreatedAtDescIdDesc(
                10L, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(message)));
        when(chatMapper.toMessageResponse(message)).thenReturn(messageResponse(99L));

        Page<MessageResponse> result = chatService.getMessages(
                10L, 1L, PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals(99L, result.getContent().get(0).getId());
    }

    @Test
    void getMessagesCursor_shouldClampLimitAndReturnNextCursor() {
        Chat chat = chat();
        Message newest = message(chat, user1, 99L, "newest");
        Message older = message(chat, user2, 98L, "older");
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(messageRepository.findPageByCursor(eq(10L), isNull(), any(Pageable.class)))
                .thenReturn(List.of(newest, older));
        when(chatMapper.toMessageResponse(newest)).thenReturn(messageResponse(99L));

        MessageCursorResponse result = chatService.getMessagesCursor(10L, null, 0, 1L);

        assertEquals(1, result.getItems().size());
        assertEquals(99L, result.getItems().get(0).getId());
        assertEquals(99L, result.getNextCursor());
        assertTrue(result.isHasMore());
        verify(messageRepository).findPageByCursor(eq(10L), isNull(),
                org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageSize() == 2));
    }

    @Test
    void getMessagesCursor_shouldReturnEverythingWhenUnderLimit() {
        Chat chat = chat();
        Message newest = message(chat, user1, 99L, "newest");
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(messageRepository.findPageByCursor(eq(10L), eq(99L), any(Pageable.class)))
                .thenReturn(List.of(newest));
        when(chatMapper.toMessageResponse(newest)).thenReturn(messageResponse(99L));

        MessageCursorResponse result = chatService.getMessagesCursor(10L, 99L, 50, 1L);

        assertEquals(1, result.getItems().size());
        assertNull(result.getNextCursor());
        assertFalse(result.isHasMore());
        verify(messageRepository).findPageByCursor(eq(10L), eq(99L),
                org.mockito.ArgumentMatchers.argThat(pageable -> pageable.getPageSize() == 51));
    }

    // ---------- read state ----------

    @Test
    void markMessagesAsRead_shouldUpdateOwnCursorAndNotifyParticipants() {
        Chat chat = chat();
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(chatUserStateRepository.findByChatIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(state(chat, user1)));
        when(messageRepository.findMaxActiveMessageId(10L)).thenReturn(99L);
        when(chatMapper.toResponse(eq(chat), eq(1L), any()))
                .thenReturn(ChatResponse.builder().id(10L).build());
        when(chatMapper.toResponse(eq(chat), eq(2L), any()))
                .thenReturn(ChatResponse.builder().id(10L).build());

        chatService.markMessagesAsRead(10L, 1L);

        verify(messageRepository).markMessagesAsRead(10L, 1L);
        verify(chatUserStateRepository).markReadUpTo(10L, 1L, 99L);
        verify(messagingTemplate)
                .convertAndSendToUser(eq("1"), eq("/queue/chat/10"), any());
        verify(messagingTemplate)
                .convertAndSendToUser(eq("2"), eq("/queue/chat/10"), any());
        verify(messagingTemplate)
                .convertAndSendToUser(eq("1"), eq("/queue/chat-updates"), any());
        verify(messagingTemplate)
                .convertAndSendToUser(eq("2"), eq("/queue/chat-updates"), any());
    }

    // ---------- deletion ----------

    @Test
    void deleteChat_shouldHideOnlyForCurrentUser() {
        Chat chat = chat();
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(chatUserStateRepository.findByChatIdAndUserId(10L, 1L))
                .thenReturn(Optional.of(state(chat, user1)));

        chatService.deleteChat(10L, 1L);

        verify(chatUserStateRepository).markDeleted(eq(10L), eq(1L), any(LocalDateTime.class));
        verify(chatUserStateRepository, never())
                .markDeleted(eq(10L), eq(2L), any(LocalDateTime.class));
        verify(messagingTemplate)
                .convertAndSendToUser(eq("1"), eq("/queue/chat/10"), any());
        verify(messagingTemplate)
                .convertAndSendToUser(eq("1"), eq("/queue/chat-updates"), any());
        verify(messagingTemplate, never())
                .convertAndSendToUser(eq("2"), anyString(), any());
    }

    @Test
    void deleteMessage_shouldRejectMessageFromAnotherChat() {
        stubParticipantChat();
        when(messageRepository.findByIdAndChatIdAndDeletedAtIsNull(99L, 10L))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> chatService.deleteMessage(10L, 99L, 1L));
        verify(messageRepository, never()).save(any());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void deleteMessage_shouldForbidDeletingSomeoneElsesMessage() {
        Chat chat = chat();
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(messageRepository.findByIdAndChatIdAndDeletedAtIsNull(99L, 10L))
                .thenReturn(Optional.of(message(chat, user2, 99L, "Bob's message")));

        assertThrows(ForbiddenException.class,
                () -> chatService.deleteMessage(10L, 99L, 1L));
        verify(messageRepository, never()).save(any());
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void deleteMessage_shouldSoftDeleteOwnMessageAndKeepHistory() {
        Chat chat = chat();
        Message message = message(chat, user1, 99L, "Alice's message");
        Message latest = message(chat, user2, 100L, "latest");
        when(chatRepository.findById(10L)).thenReturn(Optional.of(chat));
        when(messageRepository.findByIdAndChatIdAndDeletedAtIsNull(99L, 10L))
                .thenReturn(Optional.of(message));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(messageRepository.save(message)).thenReturn(message);
        when(messageRepository.findTopByChatIdAndDeletedAtIsNullOrderByIdDesc(10L))
                .thenReturn(Optional.of(latest));
        when(chatUserStateRepository.findByChatIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
        when(chatUserStateRepository.findByChatIdAndUserId(10L, 2L)).thenReturn(Optional.empty());
        when(chatMapper.toResponse(eq(chat), eq(1L), any()))
                .thenReturn(ChatResponse.builder().id(10L).build());
        when(chatMapper.toResponse(eq(chat), eq(2L), any()))
                .thenReturn(ChatResponse.builder().id(10L).build());

        chatService.deleteMessage(10L, 99L, 1L);

        assertNotNull(message.getDeletedAt());
        assertEquals(user1, message.getDeletedBy());
        assertEquals("Alice's message", message.getContent());
        assertEquals(latest.getCreatedAt(), chat.getLastMessageAt());
        verify(messageRepository).save(message);
        verify(messageRepository, never()).delete(any());
        verify(messagingTemplate)
                .convertAndSendToUser(eq("1"), eq("/queue/chat/10"), any());
        verify(messagingTemplate)
                .convertAndSendToUser(eq("2"), eq("/queue/chat/10"), any());
    }
}
