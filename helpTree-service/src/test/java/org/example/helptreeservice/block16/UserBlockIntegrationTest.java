package org.example.helptreeservice.block16;

import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.dto.helps.HelpRequest;
import org.example.helptreeservice.dto.search.SearchResultDto;
import org.example.helptreeservice.dto.users.BlockedUserResponse;
import org.example.helptreeservice.dto.users.UserBlockStatusResponse;
import org.example.helptreeservice.dto.users.UserPublicDto;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.enums.SearchType;
import org.example.helptreeservice.enums.UserBlockScope;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.ChatService;
import org.example.helptreeservice.service.HelpService;
import org.example.helptreeservice.service.SearchService;
import org.example.helptreeservice.service.UserBlockService;
import org.example.helptreeservice.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Real-database proof for Block 16: a user block is stored once per pair, expires
 * on the server and closes every approved interaction scenario in both
 * directions.
 *
 * <p>Runs with {@code INTEGRATION_TEST=true}. Every created user has a unique
 * email prefix so cleanup only touches this test's rows; the block relation
 * cascades from {@code users}.</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class UserBlockIntegrationTest {

    private static final String EMAIL_PREFIX = "block16-it-";

    @Autowired private UserBlockService userBlockService;
    @Autowired private ChatService chatService;
    @Autowired private HelpService helpService;
    @Autowired private UserService userService;
    @Autowired private SearchService searchService;
    @Autowired private UserRepository userRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private AuthorizationService authorizationService;

    @AfterEach
    void cleanUp() {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE email LIKE ?", Long.class, EMAIL_PREFIX + "%@test.local");
        if (ids.isEmpty()) {
            return;
        }
        String in = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        // Chats cascade to messages and chat_user_state.
        jdbcTemplate.update("DELETE FROM chats WHERE user1_id IN (" + in
                + ") OR user2_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM helps WHERE helper_id IN (" + in
                + ") OR receiver_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM posts WHERE user_id IN (" + in + ")");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (" + in + ")");
    }

    @Test
    void blockUpsertsOnePairAndExpiresOnTheServer() {
        User alice = createUser("alice");
        User bob = createUser("bob");

        userBlockService.blockUser(alice.getId(), bob.getId(), blockRequest(UserBlockScope.MESSAGES,
                LocalDateTime.now().plusDays(2)));

        assertEquals(1L, countPairs(alice.getId(), bob.getId()));
        assertEquals(UserBlockScope.MESSAGES, userBlockService
                .getBlockStatus(alice.getId(), bob.getId()).getScope());

        // A second POST must update the existing pair instead of creating a new one.
        userBlockService.blockUser(alice.getId(), bob.getId(), blockRequest(UserBlockScope.ALL, null));

        assertEquals(1L, countPairs(alice.getId(), bob.getId()));
        UserBlockStatusResponse status = userBlockService.getBlockStatus(alice.getId(), bob.getId());
        assertTrue(status.isBlockedByMe());
        assertFalse(status.isBlockedMe());
        assertTrue(status.isActive());
        assertEquals(UserBlockScope.ALL, status.getScope());

        List<BlockedUserResponse> blocked = userBlockService.getBlockedUsers(alice.getId());
        assertEquals(List.of(bob.getId()), blocked.stream().map(BlockedUserResponse::getUserId).toList());

        // Expiry is evaluated at read time, not by a scheduled job.
        jdbcTemplate.update("UPDATE user_blocks_relations SET created_at = "
                        + "LOCALTIMESTAMP - INTERVAL '2 days', expires_at = LOCALTIMESTAMP - INTERVAL '1 day' "
                        + "WHERE blocker_id = ? AND blocked_id = ?",
                alice.getId(), bob.getId());

        assertFalse(userBlockService.getBlockStatus(alice.getId(), bob.getId()).isActive());
        assertTrue(userBlockService.getBlockedUsers(alice.getId()).isEmpty());
        assertFalse(userBlockService.isInteractionBlocked(
                alice.getId(), bob.getId(), UserBlockScope.ALL));

        // DELETE stays idempotent even when the row has expired.
        userBlockService.unblockUser(alice.getId(), bob.getId());
        userBlockService.unblockUser(alice.getId(), bob.getId());
        assertEquals(0L, countPairs(alice.getId(), bob.getId()));
    }

    @Test
    void messagesBlockClosesChatsAndSendingInBothDirections() {
        User alice = createUser("alice");
        User bob = createUser("bob");

        Long chatId = chatService.createChat(chatRequest(bob.getId()), alice.getId()).getId();

        userBlockService.blockUser(alice.getId(), bob.getId(),
                blockRequest(UserBlockScope.MESSAGES, null));

        // The block is bidirectional: either side is stopped.
        assertThrows(ForbiddenException.class,
                () -> chatService.createChat(chatRequest(alice.getId()), bob.getId()));
        assertThrows(ForbiddenException.class,
                () -> chatService.sendMessage(chatId,
                        new org.example.helptreeservice.dto.chat.CreateMessageRequest("blocked"),
                        bob.getId()));
        assertThrows(ForbiddenException.class,
                () -> chatService.sendMessage(chatId,
                        new org.example.helptreeservice.dto.chat.CreateMessageRequest("blocked"),
                        alice.getId()));

        userBlockService.unblockUser(alice.getId(), bob.getId());

        assertEquals("allowed", chatService.sendMessage(chatId,
                new org.example.helptreeservice.dto.chat.CreateMessageRequest("allowed"),
                bob.getId()).getContent());
    }

    @Test
    void helpRequestsBlockStopsAcceptingARequest() {
        User author = createUser("author");
        User helper = createUser("helper");
        Post post = createPost(author);

        userBlockService.blockUser(helper.getId(), author.getId(),
                blockRequest(UserBlockScope.HELP_REQUESTS, null));

        assertThrows(ForbiddenException.class,
                () -> helpService.acceptHelp(helpRequest(post.getId()), helper.getId()));
        assertTrue(jdbcTemplate.queryForList(
                "SELECT id FROM helps WHERE post_id = ?", Long.class, post.getId()).isEmpty());

        userBlockService.unblockUser(helper.getId(), author.getId());

        assertEquals("ACCEPTED",
                helpService.acceptHelp(helpRequest(post.getId()), helper.getId()).getStatus());
    }

    @Test
    void profileBlockHidesThePairFromDirectoryAndSearch() {
        String token = "block16needle" + UUID.randomUUID().toString().replace("-", "");
        User alice = createUser("alice-" + token);
        User bob = createUser("bob-" + token);
        User carol = createUser("carol-" + token);

        userBlockService.blockUser(alice.getId(), bob.getId(),
                blockRequest(UserBlockScope.PROFILE, null));

        Page<UserPublicDto> aliceDirectory = userService.getAllUsersPublic(
                PageRequest.of(0, 50), token, null, alice.getId());
        assertFalse(aliceDirectory.getContent().stream()
                .anyMatch(user -> user.getId().equals(bob.getId())));
        assertTrue(aliceDirectory.getContent().stream()
                .anyMatch(user -> user.getId().equals(carol.getId())));

        // The pair is hidden from the other side too, but only for this pair.
        Page<UserPublicDto> bobDirectory = userService.getAllUsersPublic(
                PageRequest.of(0, 50), token, null, bob.getId());
        assertFalse(bobDirectory.getContent().stream()
                .anyMatch(user -> user.getId().equals(alice.getId())));
        assertTrue(bobDirectory.getContent().stream()
                .anyMatch(user -> user.getId().equals(carol.getId())));

        Page<UserPublicDto> carolDirectory = userService.getAllUsersPublic(
                PageRequest.of(0, 50), token, null, carol.getId());
        assertTrue(carolDirectory.getContent().stream()
                .anyMatch(user -> user.getId().equals(bob.getId())));

        when(authorizationService.getCurrentUser())
                .thenReturn(new AuthorizationService.UserContext(alice.getId(), "USER", null));
        PageResponse<SearchResultDto> search = searchService.search(
                token, SearchType.USER, null, null, "newest", PageRequest.of(0, 50));

        assertFalse(search.getContent().stream()
                .anyMatch(result -> result.getId().equals(bob.getId())));
        assertTrue(search.getContent().stream()
                .anyMatch(result -> result.getId().equals(carol.getId())));
    }

    private long countPairs(Long firstId, Long secondId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_blocks_relations "
                        + "WHERE blocker_id = ? AND blocked_id = ?",
                Long.class, firstId, secondId);
        return count == null ? 0L : count;
    }

    private User createUser(String name) {
        User user = new User();
        user.setName(name);
        user.setEmail(EMAIL_PREFIX + UUID.randomUUID() + "@test.local");
        user.setPassword("$2a$10$test-only-not-a-real-hash");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.saveAndFlush(user);
    }

    private Post createPost(User author) {
        Post post = new Post();
        post.setTitle("Block 16 help request " + UUID.randomUUID());
        post.setDescription("Block 16 integration post");
        post.setCategory("OTHER");
        post.setAuthorName(author.getName());
        post.setUser(author);
        post.setStatus(PostStatus.OPEN);
        post.setDeleted(false);
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        return postRepository.saveAndFlush(post);
    }

    private org.example.helptreeservice.dto.users.BlockUserRequest blockRequest(
            UserBlockScope scope, LocalDateTime expiresAt) {
        return org.example.helptreeservice.dto.users.BlockUserRequest.builder()
                .scope(scope)
                .expiresAt(expiresAt)
                .build();
    }

    private org.example.helptreeservice.dto.chat.CreateChatRequest chatRequest(Long participantId) {
        org.example.helptreeservice.dto.chat.CreateChatRequest request =
                new org.example.helptreeservice.dto.chat.CreateChatRequest();
        request.setParticipantId(participantId);
        return request;
    }

    private HelpRequest helpRequest(Long postId) {
        HelpRequest request = new HelpRequest();
        request.setPostId(postId);
        return request;
    }
}
