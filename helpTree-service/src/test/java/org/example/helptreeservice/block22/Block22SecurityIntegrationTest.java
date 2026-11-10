package org.example.helptreeservice.block22;

import jakarta.servlet.FilterChain;
import org.example.helptreeservice.dto.chat.CreateChatRequest;
import org.example.helptreeservice.dto.chat.CreateMessageRequest;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.exception.AccountBlockedException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.security.JwtAuthFilter;
import org.example.helptreeservice.security.StompAuthChannelInterceptor;
import org.example.helptreeservice.security.WsUserPrincipal;
import org.example.helptreeservice.service.AuthSessionService.IssuedTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 22.1 - security integration proof against the real PostgreSQL database.
 *
 * <p>Every check uses the production token/session/filter/STOMP code paths, not
 * mocks: a token is only accepted when the session, token version, current role
 * and account state still agree in the database.</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class Block22SecurityIntegrationTest extends Block22TestSupport {

    @Autowired private JwtAuthFilter jwtAuthFilter;
    @Autowired private StompAuthChannelInterceptor stompAuthChannelInterceptor;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void logoutRevokesOnlyTheCurrentSession() {
        User user = createUser("Logout owner");
        IssuedTokens first = issueSession(user);
        IssuedTokens second = issueSession(user);

        assertNotNull(authSessionService.validateAccessToken(
                first.userId(), first.sessionId().toString(), 0L, "USER"));

        authService.logout(user.getId(), first.sessionId(), first.refreshToken());

        assertThrows(UnauthorizedException.class, () -> authSessionService.validateAccessToken(
                first.userId(), first.sessionId().toString(), 0L, "USER"),
                "The explicitly logged-out session must stop working");
        assertNotNull(authSessionService.validateAccessToken(
                second.userId(), second.sessionId().toString(), 0L, "USER"),
                "A second device must stay signed in");

        // The surviving refresh token still rotates normally.
        assertNotNull(authService.refresh(second.refreshToken(), IP, USER_AGENT));
        assertThrows(UnauthorizedException.class,
                () -> authService.refresh(first.refreshToken(), IP, USER_AGENT));
    }

    @Test
    void logoutAllInvalidatesEveryAccessAndRefreshToken() {
        User user = createUser("Logout all owner");
        IssuedTokens first = issueSession(user);
        IssuedTokens second = issueSession(user);

        authService.logoutAll(user.getId());

        assertThrows(UnauthorizedException.class, () -> authSessionService.validateAccessToken(
                first.userId(), first.sessionId().toString(), 0L, "USER"));
        assertThrows(UnauthorizedException.class, () -> authSessionService.validateAccessToken(
                second.userId(), second.sessionId().toString(), 0L, "USER"));
        assertThrows(UnauthorizedException.class,
                () -> authService.refresh(first.refreshToken(), IP, USER_AGENT));
    }

    @Test
    void blockedAccountIsRejectedByValidatorAndHttpFilter() throws Exception {
        User user = createUser("Blocked owner");
        IssuedTokens tokens = issueSession(user);
        blockAccount(user.getId());

        assertThrows(AccountBlockedException.class, () -> authSessionService.validateAccessToken(
                tokens.userId(), tokens.sessionId().toString(), 0L, "USER"));

        MockHttpServletRequest request = authenticated("PUT", "/api/posts/1", tokens.accessToken());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = invokeJwtFilter(request, chain);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(chain);

        // The narrow self-service exception still lets a blocked user inspect
        // their own profile so the debt can be paid off.
        MockHttpServletRequest me = authenticated("GET", "/api/users/me", tokens.accessToken());
        FilterChain meChain = mock(FilterChain.class);
        MockHttpServletResponse meResponse = invokeJwtFilter(me, meChain);
        assertEquals(200, meResponse.getStatus());
        verify(meChain).doFilter(me, meResponse);
    }

    @Test
    void deletedAccountIsRejectedByValidatorAndHttpFilter() throws Exception {
        User user = createUser("Deleted owner");
        IssuedTokens tokens = issueSession(user);
        markDeleted(user.getId());

        assertThrows(UnauthorizedException.class, () -> authSessionService.validateAccessToken(
                tokens.userId(), tokens.sessionId().toString(), 0L, "USER"));

        MockHttpServletRequest request = authenticated("PUT", "/api/posts/1", tokens.accessToken());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletResponse response = invokeJwtFilter(request, chain);

        assertEquals(401, response.getStatus());
        verifyNoInteractions(chain);
    }

    @Test
    void staleRoleCannotGrantOldRights() {
        User user = createUser("Role owner");
        IssuedTokens asUser = issueSession(user);

        jdbcTemplate.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", user.getId());
        assertThrows(UnauthorizedException.class, () -> authSessionService.validateAccessToken(
                asUser.userId(), asUser.sessionId().toString(), 0L, "USER"),
                "A token with the old, lower role must not authenticate after promotion");

        User promoted = reload(user);
        IssuedTokens asAdmin = issueSession(promoted);
        assertNotNull(authSessionService.validateAccessToken(
                asAdmin.userId(), asAdmin.sessionId().toString(), 0L, "ADMIN"));

        jdbcTemplate.update("UPDATE users SET role = 'USER' WHERE id = ?", user.getId());
        assertThrows(UnauthorizedException.class, () -> authSessionService.validateAccessToken(
                asAdmin.userId(), asAdmin.sessionId().toString(), 0L, "ADMIN"),
                "A demoted admin must lose admin access on the very next request");
    }

    @Test
    void foreignWebSocketChatSubscriptionIsDenied() {
        User alice = createUser("WS Alice");
        User bob = createUser("WS Bob");
        User carol = createUser("WS Carol");
        Long aliceBobChat = chatService.createChat(chatTo(bob.getId()), alice.getId()).getId();
        Long carolChat = chatService.createChat(chatTo(bob.getId()), carol.getId()).getId();

        IssuedTokens carolTokens = issueSession(carol);
        WsUserPrincipal carolPrincipal = new WsUserPrincipal(
                carol.getId(), "USER", carolTokens.sessionId(), 0L);

        assertNull(stompAuthChannelInterceptor.preSend(
                frame(StompCommand.SUBSCRIBE, carolPrincipal, "/user/queue/chat/" + aliceBobChat), null),
                "Subscribing to another pair's chat must be dropped");
        assertNull(stompAuthChannelInterceptor.preSend(
                frame(StompCommand.SEND, carolPrincipal,
                        "/app/chat/" + aliceBobChat + "/messages"), null),
                "Sending into another pair's chat must be dropped");
        assertNull(stompAuthChannelInterceptor.preSend(
                frame(StompCommand.SUBSCRIBE, carolPrincipal, "/topic/chat/" + aliceBobChat), null),
                "Legacy chat topics must respect participation too");

        assertNotNull(stompAuthChannelInterceptor.preSend(
                frame(StompCommand.SUBSCRIBE, carolPrincipal, "/user/queue/chat/" + carolChat), null));
    }

    @Test
    void foreignChatCannotBeReadOrMutated() {
        User alice = createUser("Chat Alice");
        User bob = createUser("Chat Bob");
        User outsider = createUser("Chat Outsider");
        Long chatId = chatService.createChat(chatTo(bob.getId()), alice.getId()).getId();
        var message = chatService.sendMessage(
                chatId, new CreateMessageRequest("hello", null), alice.getId());

        assertThrows(ForbiddenException.class,
                () -> chatService.getMessages(chatId, outsider.getId(), PageRequest.of(0, 10)));
        assertThrows(ForbiddenException.class,
                () -> chatService.sendMessage(chatId, new CreateMessageRequest("intrude", null),
                        outsider.getId()));
        assertThrows(ForbiddenException.class,
                () -> chatService.deleteMessage(chatId, message.getId(), outsider.getId()));
        assertThrows(ForbiddenException.class,
                () -> chatService.deleteChat(chatId, outsider.getId()));
    }

    @Test
    void telegramIdAndFavoritesAreOwnerOrAdminOnly() {
        User owner = createUser("Profile owner");
        User outsider = createUser("Profile outsider");
        User admin = createAdmin("Profile admin");
        Post post = createPost(owner);

        userService.bindTelegramChatId(owner.getId(), "tg-secret-42");
        userService.addFavorite(owner.getId(), post.getId());

        assertEquals("tg-secret-42", userService.getTelegramChatId(owner.getId()));
        assertTrue(userService.isFavorite(owner.getId(), post.getId()));
        assertFalse(userService.isFavorite(outsider.getId(), post.getId()),
                "Favorites are scoped to their owner");

        actAs(owner.getId(), "USER");
        assertTrue(authorizationService.canManageUser(owner.getId()));
        assertFalse(authorizationService.canManageUser(outsider.getId()),
                "A user must not manage another user's profile, Telegram ID or favorites");

        actAs(outsider.getId(), "USER");
        assertFalse(authorizationService.canManageUser(owner.getId()));
        assertFalse(authorizationService.canManagePost(owner.getId()));

        actAs(admin.getId(), "ADMIN");
        assertTrue(authorizationService.canManageUser(owner.getId()));
        assertTrue(authorizationService.canManagePost(owner.getId()));
    }

    @Test
    void usersCannotManageForeignResourcesThroughAuthorizationService() {
        User alice = createUser("Auth Alice");
        User bob = createUser("Auth Bob");
        User carol = createUser("Auth Carol");
        createConfirmedHelp(bob, carol);

        actAs(alice.getId(), "USER");
        assertFalse(authorizationService.canManageUser(bob.getId()));
        assertFalse(authorizationService.canManagePost(bob.getId()));
        assertFalse(authorizationService.canAccessHelp(bob.getId(), carol.getId()));
        assertTrue(authorizationService.canAccessHelp(alice.getId(), carol.getId()));

        actAs(bob.getId(), "USER");
        assertTrue(authorizationService.canAccessHelp(bob.getId(), carol.getId()));
        assertFalse(authorizationService.canAccessHelp(alice.getId(), carol.getId()));
    }

    // --------------------------------------------------------------- helpers

    private CreateChatRequest chatTo(Long participantId) {
        CreateChatRequest request = new CreateChatRequest();
        request.setParticipantId(participantId);
        return request;
    }

    private MockHttpServletRequest authenticated(String method, String path, String token) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private MockHttpServletResponse invokeJwtFilter(MockHttpServletRequest request, FilterChain chain)
            throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        Method method = JwtAuthFilter.class.getDeclaredMethod("doFilterInternal",
                jakarta.servlet.http.HttpServletRequest.class,
                jakarta.servlet.http.HttpServletResponse.class,
                FilterChain.class);
        method.setAccessible(true);
        method.invoke(jwtAuthFilter, request, response, chain);
        return response;
    }

    private Message<byte[]> frame(StompCommand command, WsUserPrincipal principal, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setUser(principal);
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void actAs(Long userId, String role) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.setAttribute("X-User-Id", String.valueOf(userId));
        request.setAttribute("X-User-Role", role);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
