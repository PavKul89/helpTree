package org.example.helptreeservice.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.AuthConfig;
import org.springframework.stereotype.Component;

/**
 * Resolves client metadata used for the session list. Forwarded headers are only
 * trusted when explicitly enabled, because otherwise any client could forge them.
 */
@Component
@RequiredArgsConstructor
public class ClientInfoResolver {

    private final AuthConfig authConfig;

    public String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        if (authConfig.isTrustForwardedHeaders()) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String first = forwardedFor.split(",")[0].trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }

    public String resolveUserAgent(HttpServletRequest request) {
        return request == null ? null : request.getHeader("User-Agent");
    }
}
