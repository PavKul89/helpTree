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

    /** COMPLETED helps older than this are confirmed by the scheduler. */
    private long completedTimeoutHours = 168L;

    /** Allows operators to disable automatic confirmation without a code change. */
    private boolean autoConfirmEnabled = true;

    /** Allows operators to disable automatic cancellation without a code change. */
    private boolean slaEnabled = true;

    /** Enables the 50%/80% reminder sweep independently from cancellation. */
    private boolean remindersEnabled = true;

    /** Percentage of acceptedTimeoutHours at which the first reminder is sent. */
    private int reminderFirstPercent = 50;

    /** Percentage of acceptedTimeoutHours at which the second reminder is sent. */
    private int reminderSecondPercent = 80;

    /** Cron for the stale-help sweep. */
    private String slaCron = "0 0 * * * *";

    /** Cron for the SLA reminder sweep. */
    private String reminderCron = "0 0 * * * *";

    /** Maximum number of stale helps processed per sweep. */
    private int slaBatchSize = 100;

    /** Maximum number of helps processed by one reminder sweep. */
    private int reminderBatchSize = 200;

    /** Maximum rewarded confirmed helps for one helper/receiver pair per calendar month. */
    private int maxConfirmedPerPairPerMonth = 2;
}
