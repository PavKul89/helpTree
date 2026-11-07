package org.example.helptreeservice.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.ChatProperties;
import org.example.helptreeservice.repository.ChatRepository;
import org.example.helptreeservice.service.AuthSessionService;
import org.example.helptreeservice.service.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authenticates STOMP CONNECT frames with the same database-backed validation used
 * by the HTTP filter, revalidates the session on every SEND/SUBSCRIBE frame, and
 * authorizes the destination itself.
 *
 * <p>Clients may only subscribe to their own private queues and to chats they
 * participate in. Sending is allowed only to the chat message mapping; direct
 * publishing to broker destinations is rejected so a client cannot inject events
 * into another user's topic or queue.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern TOPIC_CHAT = Pattern.compile("^/topic/chat/(\\d+)$");
    private static final Pattern TOPIC_USER = Pattern.compile("^/topic/user/(\\d+)$");
    private static final Pattern USER_QUEUE_CHAT = Pattern.compile("^/user/queue/chat/(\\d+)$");
    private static final Pattern APP_CHAT_SEND = Pattern.compile("^/app/chat/(\\d+)/messages$");

    private static final Set<String> PRIVATE_USER_QUEUES = Set.of(
            "/user/queue/chat-updates",
            "/user/queue/notifications",
            "/user/queue/errors"
    );

    private final JwtService jwtService;
    private final AuthSessionService authSessionService;
    private final ChatRepository chatRepository;
    private final ChatProperties chatProperties;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (command == null) {
            // Heartbeats and other frames without a STOMP command.
            return message;
        }

        if (StompCommand.CONNECT.equals(command)) {
            return authenticateConnect(accessor) ? message : null;
        }

        if (StompCommand.DISCONNECT.equals(command)) {
            return message;
        }

        if (!StompCommand.SEND.equals(command) && !StompCommand.SUBSCRIBE.equals(command)) {
            return message;
        }

        if (!(accessor.getUser() instanceof WsUserPrincipal principal)) {
            log.debug("Отклонён STOMP-фрейм {} без аутентифицированного пользователя", command);
            return null;
        }

        if (!revalidate(principal)) {
            return null;
        }

        String destination = accessor.getDestination();
        boolean allowed = StompCommand.SUBSCRIBE.equals(command)
                ? isAllowedSubscribe(destination, principal.getUserId())
                : isAllowedSend(destination, principal.getUserId());

        if (!allowed) {
            log.warn("Пользователь {} попытался использовать запрещённый STOMP destination '{}'",
                    principal.getUserId(), destination);
            return null;
        }

        return message;
    }

    private boolean authenticateConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }

        try {
            Claims claims = jwtService.parseAccessClaims(authHeader.substring(7));
            Long userId = numberClaim(claims, JwtService.CLAIM_USER_ID);
            String role = claims.get(JwtService.CLAIM_ROLE, String.class);
            String sessionIdValue = claims.get(JwtService.CLAIM_SESSION_ID, String.class);
            Long tokenVersion = numberClaim(claims, JwtService.CLAIM_TOKEN_VERSION);

            if (userId == null || role == null || sessionIdValue == null || tokenVersion == null) {
                return false;
            }

            AuthSessionService.AuthenticatedUser user =
                    authSessionService.validateAccessToken(userId, sessionIdValue, tokenVersion, role);

            if (!matchesHandshakeUser(accessor, user.userId())) {
                log.warn("STOMP CONNECT пользователя {} не совпадает с WebSocket-рукопожатием", user.userId());
                return false;
            }

            accessor.setUser(new WsUserPrincipal(
                    user.userId(),
                    user.role(),
                    UUID.fromString(sessionIdValue),
                    user.tokenVersion()));
            return true;
        } catch (Exception e) {
            log.debug("Отклонено WebSocket-подключение: {}", e.getMessage());
            return false;
        }
    }

    /**
     * The production handshake always records the authenticated user. If the
     * attribute is present it must match the CONNECT token, so a socket opened
     * with one user's ticket cannot be used with another user's token.
     */
    private boolean matchesHandshakeUser(StompHeaderAccessor accessor, Long userId) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            return true;
        }
        Object handshakeUserId = attributes.get(WebSocketAuthInterceptor.ATTR_USER_ID);
        if (handshakeUserId == null) {
            return true;
        }
        return userId.equals(numberValue(handshakeUserId));
    }

    private boolean revalidate(WsUserPrincipal principal) {
        try {
            authSessionService.validateAccessToken(
                    principal.getUserId(),
                    principal.getSessionId().toString(),
                    principal.getTokenVersion(),
                    principal.getRole());
            return true;
        } catch (Exception e) {
            log.debug("STOMP-сессия пользователя {} больше недействительна: {}",
                    principal.getUserId(), e.getMessage());
            return false;
        }
    }

    private boolean isAllowedSubscribe(String destination, Long userId) {
        Long chatId = matchId(TOPIC_CHAT, destination);
        if (chatId != null) {
            return chatProperties.isLegacyTopicsEnabled() && isParticipant(chatId, userId);
        }

        Long topicUserId = matchId(TOPIC_USER, destination);
        if (topicUserId != null) {
            return chatProperties.isLegacyTopicsEnabled() && topicUserId.equals(userId);
        }

        Long privateChatId = matchId(USER_QUEUE_CHAT, destination);
        if (privateChatId != null) {
            return isParticipant(privateChatId, userId);
        }

        return PRIVATE_USER_QUEUES.contains(destination);
    }

    private boolean isAllowedSend(String destination, Long userId) {
        Long chatId = matchId(APP_CHAT_SEND, destination);
        return chatId != null && isParticipant(chatId, userId);
    }

    private boolean isParticipant(Long chatId, Long userId) {
        return chatRepository.existsByIdAndParticipant(chatId, userId);
    }

    private Long matchId(Pattern pattern, String destination) {
        if (destination == null) {
            return null;
        }
        Matcher matcher = pattern.matcher(destination);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long numberValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Long numberClaim(Claims claims, String name) {
        return numberValue(claims.get(name));
    }
}
