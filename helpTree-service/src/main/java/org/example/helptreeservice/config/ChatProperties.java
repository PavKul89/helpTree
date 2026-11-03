package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.chat")
public class ChatProperties {

    /** Absolute limit; the DTO annotations use the same upper bound. */
    private int maxMessageLength = 5000;

    /** Sliding one-minute window per user. */
    private boolean rateLimitEnabled = true;
    private int messagesPerMinute = 30;

    private int cursorDefaultLimit = 20;
    private int cursorMaxLimit = 100;

    /**
     * During the transition to private /user destinations the server can also
     * publish to the legacy /topic destinations for older frontend builds.
     * Both paths are authorized, but only user destinations are unguessable.
     */
    private boolean legacyTopicsEnabled = true;
}
