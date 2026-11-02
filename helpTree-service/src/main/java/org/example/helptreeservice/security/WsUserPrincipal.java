package org.example.helptreeservice.security;

import java.security.Principal;
import java.util.UUID;

public class WsUserPrincipal implements Principal {
    private final Long userId;
    private final String role;
    private final UUID sessionId;
    private final long tokenVersion;

    public WsUserPrincipal(Long userId, String role, UUID sessionId, long tokenVersion) {
        this.userId = userId;
        this.role = role;
        this.sessionId = sessionId;
        this.tokenVersion = tokenVersion;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}
