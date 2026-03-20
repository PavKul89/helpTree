package org.example.helptreeservice.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuthorizationService {

    public UserContext getCurrentUser() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        
        HttpServletRequest request = attributes.getRequest();
        String userIdStr = request.getHeader("X-User-Id");
        String role = request.getHeader("X-User-Role");
        String email = request.getHeader("X-User-Email");

        if (userIdStr == null) {
            return null;
        }

        return new UserContext(Long.parseLong(userIdStr), role != null ? role : "USER", email);
    }

    public boolean isAdmin() {
        UserContext user = getCurrentUser();
        return user != null && "ADMIN".equals(user.getRole());
    }

    public boolean isOwner(Long resourceOwnerId) {
        UserContext user = getCurrentUser();
        return user != null && user.getUserId().equals(resourceOwnerId);
    }

    public boolean canManageUser(Long targetUserId) {
        UserContext user = getCurrentUser();
        if (user == null) {
            return false;
        }
        if ("ADMIN".equals(user.getRole())) {
            return true;
        }
        return user.getUserId().equals(targetUserId);
    }

    public boolean canManagePost(Long authorId) {
        UserContext user = getCurrentUser();
        if (user == null) {
            return false;
        }
        if ("ADMIN".equals(user.getRole())) {
            return true;
        }
        return user.getUserId().equals(authorId);
    }

    public boolean canAccessHelp(Long helperId, Long receiverId) {
        UserContext user = getCurrentUser();
        if (user == null) {
            return false;
        }
        if ("ADMIN".equals(user.getRole())) {
            return true;
        }
        return user.getUserId().equals(helperId) || user.getUserId().equals(receiverId);
    }

    public static class UserContext {
        private final Long userId;
        private final String role;
        private final String email;

        public UserContext(Long userId, String role, String email) {
            this.userId = userId;
            this.role = role;
            this.email = email;
        }

        public Long getUserId() { return userId; }
        public String getRole() { return role; }
        public String getEmail() { return email; }
    }
}