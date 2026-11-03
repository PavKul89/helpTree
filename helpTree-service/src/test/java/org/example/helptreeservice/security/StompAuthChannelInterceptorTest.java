package org.example.helptreeservice.security;

import io.jsonwebtoken.Claims;
import org.example.helptreeservice.config.ChatProperties;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.repository.ChatRepository;
import org.example.helptreeservice.service.AuthSessionService;
import org.example.helptreeservice.service.AuthSessionService.AuthenticatedUser;
import org.example.helptreeservice.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private AuthSessionService authSessionService;
    @Mock
    private ChatRepository chatRepository;
    @Mock
    private ChatProperties chatProperties;

    private StompAuthChannelInterceptor interceptor;

    private static final Long USER_ID = 42L;
    private static final Long CHAT_ID = 10L;
    private static final String TOKEN = "access-token";
    private static final UUID SESSION_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthChannelInterceptor(
                jwtService, authSessionService, chatRepository, chatProperties);
    }

    private Message<byte[]> connectMessage(String authorization, Map<String, Object> sessionAttributes) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        if (sessionAttributes != null) {
            accessor.setSessionAttributes(sessionAttributes);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> connectMessage(String authorization) {
        return connectMessage(authorization, null);
    }

    private Message<byte[]> frame(StompCommand command, WsUserPrincipal principal, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (principal != null) {
            accessor.setUser(principal);
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Claims claims(String role, String sessionId, Long tokenVersion) {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtService.CLAIM_USER_ID)).thenReturn(USER_ID);
        when(claims.get(JwtService.CLAIM_ROLE, String.class)).thenReturn(role);
        when(claims.get(JwtService.CLAIM_SESSION_ID, String.class)).thenReturn(sessionId);
        when(claims.get(JwtService.CLAIM_TOKEN_VERSION)).thenReturn(tokenVersion);
        return claims;
    }

    private void authenticateConnect() {
        Claims tokenClaims = claims("USER", SESSION_ID.toString(), 3L);
        when(jwtService.parseAccessClaims(TOKEN)).thenReturn(tokenClaims);
        when(authSessionService.validateAccessToken(
                USER_ID, SESSION_ID.toString(), 3L, "USER"))
                .thenReturn(new AuthenticatedUser(USER_ID, "user@test.com", "USER",
                        SESSION_ID, 3L));
    }

    private WsUserPrincipal principal(String role) {
        return new WsUserPrincipal(USER_ID, role, SESSION_ID, 3L);
    }

    private void allowChat() {
        when(chatRepository.existsByIdAndParticipant(CHAT_ID, USER_ID)).thenReturn(true);
    }

    @Test
    void connect_shouldRejectMissingAuthorizationHeader() {
        assertNull(interceptor.preSend(connectMessage(null), null));
        verifyNoInteractions(jwtService, authSessionService);
    }

    @Test
    void connect_shouldRejectNonBearerAuthorizationHeader() {
        assertNull(interceptor.preSend(connectMessage("Basic abc"), null));
        verifyNoInteractions(jwtService, authSessionService);
    }

    @Test
    void connect_shouldAcceptValidDatabaseBackedAccessToken() {
        authenticateConnect();

        Message<?> result = interceptor.preSend(connectMessage("Bearer " + TOKEN), null);

        assertNotNull(result);
        WsUserPrincipal principal = assertInstanceOf(
                WsUserPrincipal.class, StompHeaderAccessor.wrap(result).getUser());
        assertEquals(USER_ID, principal.getUserId());
        assertEquals("USER", principal.getRole());
        assertEquals(SESSION_ID, principal.getSessionId());
        assertEquals(3L, principal.getTokenVersion());
        verify(authSessionService).validateAccessToken(
                USER_ID, SESSION_ID.toString(), 3L, "USER");
    }

    @Test
    void connect_shouldRejectWsTicketUsedAsAccessToken() {
        when(jwtService.parseAccessClaims(TOKEN))
                .thenThrow(new UnauthorizedException("ws-ticket"));

        assertNull(interceptor.preSend(connectMessage("Bearer " + TOKEN), null));
        verifyNoInteractions(authSessionService);
    }

    @Test
    void connect_shouldRejectInvalidSignatureOrExpiredToken() {
        when(jwtService.parseAccessClaims(TOKEN))
                .thenThrow(new IllegalArgumentException("invalid token"));

        assertNull(interceptor.preSend(connectMessage("Bearer " + TOKEN), null));
        verifyNoInteractions(authSessionService);
    }

    @Test
    void connect_shouldRejectRevokedOrDePrivilegedSession() {
        Claims tokenClaims = claims("ADMIN", SESSION_ID.toString(), 0L);
        when(jwtService.parseAccessClaims(TOKEN)).thenReturn(tokenClaims);
        when(authSessionService.validateAccessToken(
                USER_ID, SESSION_ID.toString(), 0L, "ADMIN"))
                .thenThrow(new UnauthorizedException("stale role"));

        assertNull(interceptor.preSend(connectMessage("Bearer " + TOKEN), null));
    }

    @Test
    void connect_shouldRejectTokenWithoutSessionClaim() {
        Claims tokenClaims = claims("USER", null, 0L);
        when(jwtService.parseAccessClaims(TOKEN)).thenReturn(tokenClaims);

        assertNull(interceptor.preSend(connectMessage("Bearer " + TOKEN), null));
        verifyNoInteractions(authSessionService);
    }

    @Test
    void connect_shouldRejectTokenOfAnotherHandshakeUser() {
        authenticateConnect();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WebSocketAuthInterceptor.ATTR_USER_ID, 99L);

        assertNull(interceptor.preSend(connectMessage("Bearer " + TOKEN, attributes), null));
    }

    @Test
    void connect_shouldAcceptTokenMatchingHandshakeUser() {
        authenticateConnect();
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WebSocketAuthInterceptor.ATTR_USER_ID, USER_ID);

        assertNotNull(interceptor.preSend(connectMessage("Bearer " + TOKEN, attributes), null));
    }

    @Test
    void send_shouldRejectFrameWithoutAuthenticatedPrincipal() {
        assertNull(interceptor.preSend(
                frame(StompCommand.SEND, null, "/app/chat/" + CHAT_ID + "/messages"), null));
        verifyNoInteractions(jwtService, authSessionService, chatRepository);
    }

    @Test
    void send_shouldAllowParticipantToSendToOwnChat() {
        allowChat();
        Message<byte[]> message = frame(
                StompCommand.SEND, principal("USER"), "/app/chat/" + CHAT_ID + "/messages");

        assertEquals(message, interceptor.preSend(message, null));
        verify(chatRepository).existsByIdAndParticipant(CHAT_ID, USER_ID);
    }

    @Test
    void send_shouldRejectForeignChatAndArbitraryDestinations() {
        when(chatRepository.existsByIdAndParticipant(CHAT_ID, USER_ID)).thenReturn(false);

        assertNull(interceptor.preSend(
                frame(StompCommand.SEND, principal("USER"), "/app/chat/" + CHAT_ID + "/messages"), null));
        assertNull(interceptor.preSend(
                frame(StompCommand.SEND, principal("USER"), "/app/anything/else"), null));
        assertNull(interceptor.preSend(
                frame(StompCommand.SEND, principal("USER"), "/topic/chat/" + CHAT_ID), null));
    }

    @Test
    void send_shouldDropFrameAfterSessionRevocationOrRoleChange() {
        WsUserPrincipal principal = principal("ADMIN");
        when(authSessionService.validateAccessToken(
                USER_ID, SESSION_ID.toString(), 3L, "ADMIN"))
                .thenThrow(new UnauthorizedException("role changed"));

        assertNull(interceptor.preSend(frame(
                StompCommand.SEND, principal, "/app/chat/" + CHAT_ID + "/messages"), null));
    }

    @Test
    void subscribe_shouldRevalidateSession() {
        when(authSessionService.validateAccessToken(
                USER_ID, SESSION_ID.toString(), 3L, "USER"))
                .thenThrow(new UnauthorizedException("revoked"));

        assertNull(interceptor.preSend(frame(
                StompCommand.SUBSCRIBE, principal("USER"), "/user/queue/chat-updates"), null));
    }

    @Test
    void subscribe_shouldAllowOwnPrivateQueues() {
        assertNotNull(interceptor.preSend(frame(
                StompCommand.SUBSCRIBE, principal("USER"), "/user/queue/chat-updates"), null));
        assertNotNull(interceptor.preSend(frame(
                StompCommand.SUBSCRIBE, principal("USER"), "/user/queue/errors"), null));
    }

    @Test
    void subscribe_shouldAllowOwnChatAndRejectForeignChat() {
        allowChat();

        assertNotNull(interceptor.preSend(frame(
                StompCommand.SUBSCRIBE, principal("USER"), "/user/queue/chat/" + CHAT_ID), null));
        assertNull(interceptor.preSend(frame(
                StompCommand.SUBSCRIBE, principal("USER"), "/user/queue/chat/99"), null));
    }

    @Test
    void subscribe_shouldRejectArbitraryDestinations() {
        assertNull(interceptor.preSend(frame(
                StompCommand.SUBSCRIBE, principal("USER"), "/topic/other"), null));
        assertNull(interceptor.preSend(frame(
                StompCommand.SUBSCRIBE, principal("USER"), "/queue/anything"), null));
    }

    @Test
    void subscribe_shouldRejectForeignLegacyUserTopic() {
        when(chatProperties.isLegacyTopicsEnabled()).thenReturn(true);

        assertNull(interceptor.preSend(frame(
                StompCommand.SUBSCRIBE, principal("USER"), "/topic/user/99"), null));
        assertNotNull(interceptor.preSend(frame(
                StompCommand.SUBSCRIBE, principal("USER"), "/topic/user/" + USER_ID), null));
    }

    @Test
    void subscribe_shouldRespectLegacyTopicsSwitch() {
        when(chatProperties.isLegacyTopicsEnabled()).thenReturn(false);

        assertNull(interceptor.preSend(frame(
                StompCommand.SUBSCRIBE, principal("USER"), "/topic/user/" + USER_ID), null));
    }

    @Test
    void subscribe_shouldAllowLegacyChatOnlyForParticipants() {
        when(chatProperties.isLegacyTopicsEnabled()).thenReturn(true);
        allowChat();

        assertNotNull(interceptor.preSend(frame(
                StompCommand.SUBSCRIBE, principal("USER"), "/topic/chat/" + CHAT_ID), null));
        assertNull(interceptor.preSend(frame(
                StompCommand.SUBSCRIBE, principal("USER"), "/topic/chat/99"), null));
    }
}
