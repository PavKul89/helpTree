package org.example.helptreeservice.service.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.LoginProtectionProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

/**
 * Temporary login lockout backed by the shared database.
 *
 * <p>The counter key is a hash of (client IP, normalized email), not the email
 * alone. This is intentional: an attacker who knows a victim's address cannot
 * lock the victim out from their own IP. A successful login clears both the
 * counter and the lock, and the lock expires automatically.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginProtectionService {

    private static final String FAILURE_PREFIX = "login-failure:";
    private static final String LOCK_PREFIX = "login-lock:";

    private final RateLimitStore store;
    private final LoginProtectionProperties properties;

    /**
     * @return seconds until the (IP, account) pair may try again, or empty when
     *         it is not locked or the shared store is temporarily unavailable.
     */
    public Optional<Long> retryAfterSeconds(String ipAddress, String email) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        try {
            return store.findActiveLock(lockKey(ipAddress, email));
        } catch (RuntimeException ex) {
            // Availability over strictness: a database hiccup must not make login
            // impossible. The failure is visible in logs and metrics.
            log.error("Could not read login lockout state; allowing the attempt", ex);
            return Optional.empty();
        }
    }

    /**
     * Records a failed attempt and creates a lock once the threshold is reached.
     *
     * @return the lockout duration in seconds when this failure locked the
     *         (IP, account) pair, or empty when it did not.
     */
    public Optional<Long> recordFailure(String ipAddress, String email) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        String failureKey = failureKey(ipAddress, email);
        try {
            Duration window = Duration.ofMinutes(Math.max(1, properties.getFailureWindowMinutes()));
            RateLimitDecision decision = store.increment(
                    failureKey,
                    Math.max(1, properties.getMaxAttempts()),
                    window.toSeconds());

            long lockoutSeconds = Duration.ofMinutes(
                    Math.max(1, properties.getLockoutMinutes())).toSeconds();
            if (!decision.allowed() || decision.remaining() <= 0) {
                store.acquireLock(lockKey(ipAddress, email), lockoutSeconds);
                // Start with a clean counter after a lock, so the next series of
                // attempts after expiry gets a full budget.
                store.delete(failureKey);
                return Optional.of(lockoutSeconds);
            }
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.error("Could not record a failed login attempt", ex);
            return Optional.empty();
        }
    }

    /** Clears temporary failures after a successful authentication. */
    public void recordSuccess(String ipAddress, String email) {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            store.delete(failureKey(ipAddress, email));
            store.deleteLock(lockKey(ipAddress, email));
        } catch (RuntimeException ex) {
            log.error("Could not clear login failure state after a successful login", ex);
        }
    }

    private String failureKey(String ipAddress, String email) {
        return RateLimitKeys.normalize(FAILURE_PREFIX
                + RateLimitKeys.hashed(normalizeIp(ipAddress), normalizeEmail(email)));
    }

    private String lockKey(String ipAddress, String email) {
        return RateLimitKeys.normalize(LOCK_PREFIX
                + RateLimitKeys.hashed(normalizeIp(ipAddress), normalizeEmail(email)));
    }

    private String normalizeIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return "unknown";
        }
        return ipAddress.trim();
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
