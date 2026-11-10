package org.example.helptreeservice.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.AuthConfig;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;

/**
 * Resolves client metadata used for the session list. Forwarded headers are only
 * trusted when explicitly enabled, because otherwise any client could forge them.
 * When enabled, the reverse proxy must overwrite (not append to) X-Forwarded-For,
 * as described in {@link org.example.helptreeservice.filter.RateLimiterFilter}.
 */
@Component
@RequiredArgsConstructor
public class ClientInfoResolver {

    private static final Pattern IPV4_LITERAL = Pattern.compile("(?:\\d{1,3}\\.){3}\\d{1,3}");
    private static final Pattern IPV6_LITERAL = Pattern.compile("[0-9a-fA-F:.]+");

    private final AuthConfig authConfig;

    public String resolveIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        if (authConfig.isTrustForwardedHeaders()) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                String first = forwardedFor.split(",")[0].trim();
                if (isNumericIpAddress(first)) {
                    return first;
                }
                return request.getRemoteAddr();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                String address = realIp.trim();
                if (isNumericIpAddress(address)) {
                    return address;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private boolean isNumericIpAddress(String address) {
        boolean ipv4 = IPV4_LITERAL.matcher(address).matches();
        boolean ipv6 = address.indexOf(':') >= 0 && IPV6_LITERAL.matcher(address).matches();
        if (!ipv4 && !ipv6) {
            return false;
        }
        try {
            InetAddress.getByName(address);
            return true;
        } catch (UnknownHostException ex) {
            return false;
        }
    }

    public String resolveUserAgent(HttpServletRequest request) {
        return request == null ? null : request.getHeader("User-Agent");
    }
}
