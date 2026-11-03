package org.example.helptreeservice.security;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.service.AuthSessionService;
import org.example.helptreeservice.service.JwtService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * Rejects WebSocket upgrades that do not carry a valid, short-lived handshake
 * ticket. Browsers cannot set an Authorization header on the upgrade request, so
 * the ticket is passed in the query string (or an Authorization header for
 * non-browser clients) and is validated against the database before the socket
 * is accepted. The STOMP CONNECT frame is authenticated separately with the full
 * access token as defense in depth.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "wsUserId";
    public static final String ATTR_ROLE = "wsRole";
    public static final String ATTR_SESSION_ID = "wsSessionId";
    public static final String ATTR_TOKEN_VERSION = "wsTokenVersion";

    private final JwtService jwtService;
    private final AuthSessionService authSessionService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        String ticket = extractTicket(request);
        if (ticket == null || ticket.isBlank()) {
            log.debug("Отклонено WebSocket-рукопожатие без тикета");
            return false;
        }

        try {
            Claims claims = jwtService.parseWsTicket(ticket);
            Long userId = numberClaim(claims, JwtService.CLAIM_USER_ID);
            String role = claims.get(JwtService.CLAIM_ROLE, String.class);
            String sessionIdValue = claims.get(JwtService.CLAIM_SESSION_ID, String.class);
            Long tokenVersion = numberClaim(claims, JwtService.CLAIM_TOKEN_VERSION);

            if (userId == null || role == null || sessionIdValue == null || tokenVersion == null) {
                return false;
            }

            AuthSessionService.AuthenticatedUser user = authSessionService.validateAccessToken(
                    userId, sessionIdValue, tokenVersion, role);

            attributes.put(ATTR_USER_ID, user.userId());
            attributes.put(ATTR_ROLE, user.role());
            attributes.put(ATTR_SESSION_ID, user.sessionId().toString());
            attributes.put(ATTR_TOKEN_VERSION, user.tokenVersion());
            return true;
        } catch (Exception e) {
            log.debug("Отклонено WebSocket-рукопожатие: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }

    private String extractTicket(ServerHttpRequest request) {
        String fromQuery = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("ticket");
        if (fromQuery != null && !fromQuery.isBlank()) {
            return fromQuery;
        }

        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
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
