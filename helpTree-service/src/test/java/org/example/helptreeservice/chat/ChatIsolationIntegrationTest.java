package org.example.helptreeservice.chat;

import org.example.helptreeservice.dto.chat.ChatResponse;
import org.example.helptreeservice.dto.chat.CreateChatRequest;
import org.example.helptreeservice.dto.chat.CreateMessageRequest;
import org.example.helptreeservice.dto.chat.MessageCursorResponse;
import org.example.helptreeservice.dto.chat.MessageResponse;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.repository.ChatRepository;
import org.example.helptreeservice.repository.MessageRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.ChatService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * End-to-end isolation checks against the real database: chats, message history,
 * unread counters, deletion and WebSocket destinations must be scoped to the
 * authenticated participant and to nobody else.
 *
 * <p>Run with {@code INTEGRATION_TEST=true}. Chat events are dispatched after a
 * real commit, so the test deliberately does not wrap itself in a transaction
 * (that would defer the events until the test rollback). It removes its own users
 * and chats afterwards instead.</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class ChatIsolationIntegrationTest {

    @Autowired
    private ChatService chatService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ChatRepository chatRepository;
    @Autowired
    private MessageRepository messageRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SimpMessagingTemplate messagingTemplate;

    private final List<Long> createdUserIds = new ArrayList<>();

    private User createUser(String name) {
        User user = new User();
        user.setName(name);
        user.setEmail("chat-it-" + UUID.randomUUID() + "@test.local");
        user.setPassword("$2a$10$test-only-not-a-real-hash");
        User saved = userRepository.saveAndFlush(user);
        createdUserIds.add(saved.getId());
        return saved;
    }

    @AfterEach
    void removeCreatedData() {
        if (createdUserIds.isEmpty()) {
            return;
        }
        String ids = createdUserIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        // chats cascade to messages and chat_user_state
        jdbcTemplate.update("DELETE FROM chats WHERE user1_id IN (" + ids
                + ") OR user2_id IN (" + ids + ")");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (" + ids + ")");
        createdUserIds.clear();
    }

    private Long createChat(Long from, Long to) {
        CreateChatRequest request = new CreateChatRequest();
        request.setParticipantId(to);
        return chatService.createChat(request, from).getId();
    }

    private ChatResponse findChat(List<ChatResponse> chats, Long chatId) {
        return chats.stream()
                .filter(chat -> chat.getId().equals(chatId))
                .findFirst()
                .orElse(null);
    }

    private MessageResponse send(Long chatId, Long userId, String content) {
        return chatService.sendMessage(chatId, new CreateMessageRequest(content), userId);
    }

    @Test
    void chatHistoryAndUnreadStateAreIsolatedPerUser() {
        User alice = createUser("Alice");
        User bob = createUser("Bob");
        User carol = createUser("Carol");

        Long aliceBobChat = createChat(alice.getId(), bob.getId());
        Long bobCarolChat = createChat(carol.getId(), bob.getId());

        assertEquals(aliceBobChat, createChat(bob.getId(), alice.getId()),
                "Reverse pair must resolve to the same chat");
        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chats WHERE LEAST(user1_id, user2_id) = ? "
                        + "AND GREATEST(user1_id, user2_id) = ?",
                Long.class, alice.getId(), bob.getId()));

        assertEquals(List.of(aliceBobChat),
                chatService.getChats(alice.getId()).stream().map(ChatResponse::getId).toList());
        assertEquals(List.of(bobCarolChat),
                chatService.getChats(carol.getId()).stream().map(ChatResponse::getId).toList());
        assertEquals(2, chatService.getChats(bob.getId()).size());

        MessageResponse message = send(aliceBobChat, alice.getId(), "hello Bob");

        verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(alice.getId().toString()), eq("/queue/chat/" + aliceBobChat), any());
        verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(bob.getId().toString()), eq("/queue/chat/" + aliceBobChat), any());
        verify(messagingTemplate, never()).convertAndSendToUser(
                eq(carol.getId().toString()), anyString(), any());

        assertEquals(0L, findChat(chatService.getChats(alice.getId()), aliceBobChat)
                .getUnreadCount());
        assertEquals(1L, findChat(chatService.getChats(bob.getId()), aliceBobChat)
                .getUnreadCount());

        Page<MessageResponse> bobHistory = chatService.getMessages(
                aliceBobChat, bob.getId(), PageRequest.of(0, 20));
        assertEquals(List.of(message.getId()),
                bobHistory.getContent().stream().map(MessageResponse::getId).toList());

        assertThrows(ForbiddenException.class,
                () -> chatService.getMessages(aliceBobChat, carol.getId(), PageRequest.of(0, 20)));
        assertThrows(ForbiddenException.class,
                () -> send(aliceBobChat, carol.getId(), "intrusion"));
        assertThrows(ForbiddenException.class,
                () -> chatService.markMessagesAsRead(aliceBobChat, carol.getId()));
        assertThrows(ForbiddenException.class,
                () -> chatService.deleteChat(aliceBobChat, carol.getId()));

        chatService.markMessagesAsRead(aliceBobChat, bob.getId());
        assertEquals(0L, findChat(chatService.getChats(bob.getId()), aliceBobChat)
                .getUnreadCount());
    }

    @Test
    void deletingAChatAffectsOnlyTheCallerAndIsRestoredByANewMessage() {
        User alice = createUser("Alice");
        User bob = createUser("Bob");
        Long chatId = createChat(alice.getId(), bob.getId());
        MessageResponse first = send(chatId, alice.getId(), "first");
        chatService.markMessagesAsRead(chatId, bob.getId());

        clearInvocations(messagingTemplate);
        chatService.deleteChat(chatId, bob.getId());

        assertNull(findChat(chatService.getChats(bob.getId()), chatId));
        assertNotNull(findChat(chatService.getChats(alice.getId()), chatId));
        assertEquals(List.of(first.getId()), chatService.getMessages(
                        chatId, bob.getId(), PageRequest.of(0, 20))
                .getContent().stream().map(MessageResponse::getId).toList());

        verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(bob.getId().toString()), eq("/queue/chat/" + chatId), any());
        verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(bob.getId().toString()), eq("/queue/chat-updates"), any());
        verify(messagingTemplate, never()).convertAndSendToUser(
                eq(alice.getId().toString()), anyString(), any());

        MessageResponse second = send(chatId, alice.getId(), "second");

        assertNotNull(findChat(chatService.getChats(bob.getId()), chatId),
                "A new message must make the chat visible again for the recipient");
        assertEquals(1L, findChat(chatService.getChats(bob.getId()), chatId).getUnreadCount());
        assertEquals(List.of(second.getId(), first.getId()), chatService.getMessages(
                        chatId, bob.getId(), PageRequest.of(0, 20))
                .getContent().stream().map(MessageResponse::getId).toList());
    }

    @Test
    void deletingAMessageIsSoftAndDoesNotLeakToTheOtherParticipantHistory() {
        User alice = createUser("Alice");
        User bob = createUser("Bob");
        Long chatId = createChat(alice.getId(), bob.getId());
        MessageResponse message = send(chatId, alice.getId(), "keep the row");

        clearInvocations(messagingTemplate);
        chatService.deleteMessage(chatId, message.getId(), alice.getId());

        Boolean deleted = jdbcTemplate.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM messages WHERE id = ?",
                Boolean.class, message.getId());
        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM messages WHERE id = ?", String.class, message.getId());
        assertEquals(Boolean.TRUE, deleted);
        assertEquals("keep the row", content);

        assertFalse(chatService.getMessages(chatId, alice.getId(), PageRequest.of(0, 20))
                .getContent().stream()
                .anyMatch(item -> item.getId().equals(message.getId())));
        assertFalse(chatService.getMessages(chatId, bob.getId(), PageRequest.of(0, 20))
                .getContent().stream()
                .anyMatch(item -> item.getId().equals(message.getId())));
        assertNull(chatRepository.findById(chatId).orElseThrow().getLastMessageAt());

        verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(alice.getId().toString()), eq("/queue/chat/" + chatId), any());
        verify(messagingTemplate, atLeastOnce()).convertAndSendToUser(
                eq(bob.getId().toString()), eq("/queue/chat/" + chatId), any());
    }

    @Test
    void cursorPaginationReturnsStablePagesWithinOneChat() {
        User alice = createUser("Alice");
        User bob = createUser("Bob");
        Long chatId = createChat(alice.getId(), bob.getId());
        MessageResponse first = send(chatId, alice.getId(), "one");
        MessageResponse second = send(chatId, alice.getId(), "two");
        MessageResponse third = send(chatId, alice.getId(), "three");

        MessageCursorResponse page1 = chatService.getMessagesCursor(chatId, null, 2, alice.getId());

        assertEquals(List.of(third.getId(), second.getId()),
                page1.getItems().stream().map(MessageResponse::getId).toList());
        assertTrue(page1.isHasMore());
        assertEquals(second.getId(), page1.getNextCursor());

        MessageCursorResponse page2 = chatService.getMessagesCursor(
                chatId, page1.getNextCursor(), 2, alice.getId());

        assertEquals(List.of(first.getId()),
                page2.getItems().stream().map(MessageResponse::getId).toList());
        assertFalse(page2.isHasMore());
        assertNull(page2.getNextCursor());
    }
}
