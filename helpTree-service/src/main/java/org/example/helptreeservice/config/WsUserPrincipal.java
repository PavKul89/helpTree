package org.example.helptreeservice.config;

import java.security.Principal;

public class WsUserPrincipal implements Principal {
    private final Long userId;
    private final String role;

    public WsUserPrincipal(Long userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    @Override
    public String getName() {
        return userId.toString();
    }
}
