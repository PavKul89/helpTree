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

    /** A sender may edit their own message for this many minutes. */
    private int editWindowMinutes = 15;

    /** Maximum number of image attachments on one message. */
    private int maxAttachments = 3;

    /**
     * Messages older than this many days are physically deleted. 0 disables the
     * retention sweep; negative values are rejected at startup by the validator.
     */
    private int retentionDays = 90;

    /** Cron for the retention sweep. */
    private String retentionCron = "0 30 3 * * *";

    /** Maximum number of messages deleted per batch/transaction. */
    private int retentionBatchSize = 500;

    /** Safety cap on how many batches one sweep may delete. */
    private int retentionMaxBatchesPerRun = 20;

    /**
     * During the transition to private /user destinations the server can also
     * publish to the legacy /topic destinations for older frontend builds.
     * Both paths are authorized, but only user destinations are unguessable.
     */
    private boolean legacyTopicsEnabled = true;
}
