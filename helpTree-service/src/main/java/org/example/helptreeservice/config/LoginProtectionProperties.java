package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Temporary login lockout. Failures are counted per (client IP, normalized
 * email), never per email alone, so an attacker cannot lock a victim out from
 * an unrelated address. The state lives in the shared database.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.login-protection")
public class LoginProtectionProperties {

    private boolean enabled = true;

    /** Failures inside the window that trigger a lockout. */
    private int maxAttempts = 5;

    /** How long the failure counter keeps accumulating. */
    private int failureWindowMinutes = 15;

    /** How long a locked (IP, account) pair is rejected. */
    private int lockoutMinutes = 15;
}
