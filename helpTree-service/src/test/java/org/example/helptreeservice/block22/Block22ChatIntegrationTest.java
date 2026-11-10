package org.example.helptreeservice.block22;

import org.example.helptreeservice.dto.chat.ChatResponse;
import org.example.helptreeservice.dto.chat.CreateChatRequest;
import org.example.helptreeservice.dto.chat.CreateMessageRequest;
import org.example.helptreeservice.dto.chat.MessageResponse;
import org.example.helptreeservice.dto.users.BlockUserRequest;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.UserBlockScope;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 22.4 - chat scoping and race proof against real rows.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class Block22ChatIntegrationTest extends Block22TestSupport {

    @Test
    void aMessageCannotBeDeletedThroughAnotherChatUrl() {
        User alice = createUser("URL Alice");
        User bob = createUser("URL Bob");
        User carol = createUser("URL Carol");
        Long aliceBob = createChat(alice, bob);
        Long aliceCarol = createChat(alice, carol);
        MessageResponse message = send(aliceBob, alice, "only in alice-bob");

        assertThrows(NotFoundException.class,
                () -> chatService.deleteMessage(aliceCarol, message.getId(), alice.getId()),
                "A message id must be resolved inside the chat id from the URL");
        assertNotNull(messageRepository.findById(message.getId()).orElseThrow().getDeletedAt() == null
                ? message : null, "The message must survive the cross-chat delete attempt");
    }

    @Test
    void aForeignChatCannotBeUsedToDeleteAMessage() {
        User alice = createUser("Delete Alice");
        User bob = createUser("Delete Bob");
        User outsider = createUser("Delete Outsider");
        Long chatId = createChat(alice, bob);
        MessageResponse message = send(chatId, alice, "keep me");

        assertThrows(ForbiddenException.class,
                () -> chatService.deleteMessage(chatId, message.getId(), outsider.getId()));
    }

    @Test
    void onlyTheSenderCanDeleteTheirMessage() {
        User alice = createUser("Sender Alice");
        User bob = createUser("Sender Bob");
        Long chatId = createChat(alice, bob);
        MessageResponse message = send(chatId, alice, "mine");

        assertThrows(ForbiddenException.class,
                () -> chatService.deleteMessage(chatId, message.getId(), bob.getId()));
        chatService.deleteMessage(chatId, message.getId(), alice.getId());
        assertNotNull(messageRepository.findById(message.getId()).orElseThrow().getDeletedAt());
    }

    @Test
    void duplicateChatsArePreventedEvenUnderConcurrency() throws Exception {
        User alice = createUser("Duplicate Alice");
        User bob = createUser("Duplicate Bob");
        int attempts = 6;

        List<Object> results = runConcurrentlyAllowFailure(attempts, index -> {
            User first = index % 2 == 0 ? alice : bob;
            User second = index % 2 == 0 ? bob : alice;
            return chatService.createChat(chatTo(second.getId()), first.getId());
        });

        results.forEach(result -> assertEquals(ChatResponse.class, result.getClass()));
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM chats WHERE LEAST(user1_id, user2_id) = ? "
                        + "AND GREATEST(user1_id, user2_id) = ?",
                Long.class, alice.getId(), bob.getId());
        assertEquals(1L, count, "The normalized pair unique index must hold under race");

        long distinctIds = results.stream()
                .map(ChatResponse.class::cast)
                .map(ChatResponse::getId)
                .distinct()
                .count();
        assertEquals(1L, distinctIds, "Every caller must receive the same chat");
    }

    @Test
    void deletingAChatIsPrivateToTheCallerAndRestorable() {
        User alice = createUser("Hide Alice");
        User bob = createUser("Hide Bob");
        Long chatId = createChat(alice, bob);
        send(chatId, alice, "first");

        chatService.deleteChat(chatId, bob.getId());
        assertNull(findChat(chatService.getChats(bob.getId()), chatId));
        assertNotNull(findChat(chatService.getChats(alice.getId()), chatId));
        assertEquals(1, chatService.getMessages(chatId, bob.getId(), PageRequest.of(0, 20)).getContent().size(),
                "Hiding a chat must not erase the messages for the other participant");

        send(chatId, alice, "brings it back");
        assertNotNull(findChat(chatService.getChats(bob.getId()), chatId),
                "A new message restores the chat for a user who hid it");
    }

    @Test
    void unreadCounterAndLastReadCursorFollowTheActiveMessages() {
        User alice = createUser("Unread Alice");
        User bob = createUser("Unread Bob");
        Long chatId = createChat(alice, bob);

        MessageResponse first = send(chatId, alice, "one");
        assertEquals(1L, findChat(chatService.getChats(bob.getId()), chatId).getUnreadCount());
        assertEquals(0L, findChat(chatService.getChats(alice.getId()), chatId).getUnreadCount(),
                "The sender's own message is already read for the sender");

        chatService.markMessagesAsRead(chatId, bob.getId());
        assertEquals(0L, findChat(chatService.getChats(bob.getId()), chatId).getUnreadCount());
        assertEquals(first.getId(), lastReadMessageId(chatId, bob.getId()));

        MessageResponse second = send(chatId, alice, "two");
        assertEquals(1L, findChat(chatService.getChats(bob.getId()), chatId).getUnreadCount());
        assertEquals(first.getId(), lastReadMessageId(chatId, bob.getId()),
                "An unread message must not move the cursor");

        chatService.markMessagesAsRead(chatId, bob.getId());
        assertEquals(second.getId(), lastReadMessageId(chatId, bob.getId()));
        assertEquals(0L, findChat(chatService.getChats(bob.getId()), chatId).getUnreadCount());
    }

    @Test
    void aMessagesScopeBlockPreventsNewChatsAndNewMessages() {
        User alice = createUser("Block Alice");
        User bob = createUser("Block Bob");
        Long chatId = createChat(alice, bob);
        send(chatId, alice, "before the block");

        BlockUserRequest request = new BlockUserRequest();
        request.setScope(UserBlockScope.MESSAGES);
        userBlockService.blockUser(alice.getId(), bob.getId(), request);

        assertThrows(ForbiddenException.class,
                () -> chatService.sendMessage(chatId, new CreateMessageRequest("after", null), bob.getId()));
        User carol = createUser("Block Carol");
        assertNotNull(chatService.createChat(chatTo(carol.getId()), bob.getId()),
                "A MESSAGES block only covers the blocked pair; unrelated users can still chat");

        userBlockService.unblockUser(alice.getId(), bob.getId());
        assertNotNull(chatService.sendMessage(
                chatId, new CreateMessageRequest("unblocked", null), bob.getId()));
    }

    // --------------------------------------------------------------- helpers

    private Long createChat(User first, User second) {
        return chatService.createChat(chatTo(second.getId()), first.getId()).getId();
    }

    private CreateChatRequest chatTo(Long participantId) {
        CreateChatRequest request = new CreateChatRequest();
        request.setParticipantId(participantId);
        return request;
    }

    private MessageResponse send(Long chatId, User sender, String content) {
        return chatService.sendMessage(chatId, new CreateMessageRequest(content, null), sender.getId());
    }

    private ChatResponse findChat(List<ChatResponse> chats, Long chatId) {
        return chats.stream()
                .filter(chat -> chat.getId().equals(chatId))
                .findFirst()
                .orElse(null);
    }

    private Long lastReadMessageId(Long chatId, Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT last_read_message_id FROM chat_user_state WHERE chat_id = ? AND user_id = ?",
                Long.class, chatId, userId);
    }
}
