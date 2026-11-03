package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.help")
public class HelpProperties {

    /** Stale ACCEPTED helps older than this are cancelled by the scheduler. */
    private long acceptedTimeoutHours = 168L;

    /** Allows operators to disable automatic cancellation without a code change. */
    private boolean slaEnabled = true;

    /** Cron for the stale-help sweep. */
    private String slaCron = "0 0 * * * *";

    /** Maximum number of stale helps processed per sweep. */
    private int slaBatchSize = 100;
}
