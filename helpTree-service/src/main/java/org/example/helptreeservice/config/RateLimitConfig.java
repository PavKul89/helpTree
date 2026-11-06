package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Server-side request limits. Every instance uses the same shared counter
 * store, so these values are per IP/account/route and not per JVM.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitConfig {

    private boolean enabled = true;

    /** Window used by every rule unless a rule overrides it. */
    private int windowSeconds = 60;

    /** Authenticated or public routes that have no more specific rule. */
    private int defaultLimit = 600;

    /** Authenticated /api/auth/** endpoints (session management, logout, ...). */
    private int authLimit = 60;

    /** POST /api/auth/login, counted per client IP. */
    private int loginLimit = 10;

    /** POST /api/auth/register, counted per client IP. */
    private int registerLimit = 5;

    /** Password-reset request/confirm and email-token endpoints. */
    private int passwordResetLimit = 5;

    /** Image and avatar uploads. */
    private int uploadLimit = 20;

    /** State-changing social endpoints (posts, comments, helps, reviews). */
    private int createLimit = 60;

    /** Cron for deleting expired counters and locks. */
    private String cleanupCron = "0 */5 * * * *";

    /** Upper bound for one cleanup transaction. */
    private int cleanupBatchSize = 1000;
}
