package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Settings for the centralized notification domain.
 *
 * <p>The dispatcher is deliberately conservative: a single instance processes a
 * bounded batch, retries transient delivery failures with exponential backoff,
 * and moves exhausted events to {@code DEAD} instead of retrying forever.</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.notifications")
public class NotificationProperties {

    /** Global kill switch. When false no notification row is created at all. */
    private boolean enabled = true;

    /** How often the durable outbox is swept. */
    private String dispatchCron = "0 * * * * *";

    /** Maximum rows claimed by one claim transaction. */
    private int batchSize = 50;

    /** Upper bound on claim/deliver rounds per scheduler tick. */
    private int maxBatchesPerRun = 10;

    /** Attempts include the first claim; after this count the event is DEAD. */
    private int maxAttempts = 5;

    /** First retry delay in seconds; doubles on every failure. */
    private long retryBaseSeconds = 30;

    /** Retry delay ceiling in seconds. */
    private long retryMaxSeconds = 3600;

    /**
     * A PROCESSING row older than this is considered abandoned (the worker died)
     * and is returned to the retry pool.
     */
    private long processingTimeoutMinutes = 10;

    /** Telegram transport stays disabled while this is blank. */
    private String telegramBotToken = "";

    private String telegramApiUrl = "https://api.telegram.org";
}
