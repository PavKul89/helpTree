package org.example.helptreeservice.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.service.AuthSessionService;
import org.example.helptreeservice.service.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Authenticates STOMP CONNECT frames with the same database-backed validation used
 * by the HTTP filter, and revalidates the session on later frames so a revoked or
 * de-privileged session cannot keep sending messages.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private final JwtService jwtService;
    private final AuthSessionService authSessionService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            return authenticateConnect(accessor) ? message : null;
        }

        if (StompCommand.SEND.equals(accessor.getCommand())
                || StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return revalidate(accessor, message);
        }

        return message;
    }

    private boolean authenticateConnect(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return false;
        }

        try {
            Claims claims = jwtService.parseClaims(authHeader.substring(7));
            Long userId = numberClaim(claims, JwtService.CLAIM_USER_ID);
            String role = claims.get(JwtService.CLAIM_ROLE, String.class);
            String sessionIdValue = claims.get(JwtService.CLAIM_SESSION_ID, String.class);
            Long tokenVersion = numberClaim(claims, JwtService.CLAIM_TOKEN_VERSION);

            if (userId == null || sessionIdValue == null || tokenVersion == null) {
                return false;
            }

            AuthSessionService.AuthenticatedUser user =
                    authSessionService.validateAccessToken(userId, sessionIdValue, tokenVersion, role);

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

    private Message<?> revalidate(StompHeaderAccessor accessor, Message<?> message) {
        if (!(accessor.getUser() instanceof WsUserPrincipal principal)) {
            log.debug("Отклонён STOMP-фрейм без аутентифицированного пользователя");
            return null;
        }

        try {
            authSessionService.validateAccessToken(
                    principal.getUserId(),
                    principal.getSessionId().toString(),
                    principal.getTokenVersion(),
                    principal.getRole());
            return message;
        } catch (Exception e) {
            log.debug("STOMP-сессия пользователя {} больше недействительна: {}",
                    principal.getUserId(), e.getMessage());
            return null;
        }
    }

    private Long numberClaim(Claims claims, String name) {
        Object value = claims.get(name);
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
}
