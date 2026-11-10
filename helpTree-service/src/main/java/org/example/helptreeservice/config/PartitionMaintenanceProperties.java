package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Runtime maintenance window for the range-partitioned hot tables
 * ({@code messages}, {@code outbox_events}).
 *
 * <p>Migrations create an initial window and a default partition. This scheduler
 * keeps that window rolling forward so a write never falls into the default
 * partition during normal operation, and optionally retires whole months. Old
 * partitions are only dropped when they are past the retention policy; a
 * messages partition also has to be older than the chat retention window, so a
 * long retention setting can never lose data that the product still promises to
 * keep.</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.partitions")
public class PartitionMaintenanceProperties {

    /** Master switch. When false the scheduler performs no DDL at all. */
    private boolean enabled = true;

    /** Daily maintenance window; low traffic, after the chat retention sweep. */
    private String cron = "0 15 3 * * *";

    /**
     * Number of future months to keep pre-created. Three means the current
     * month plus the next three are always available, leaving room for a few
     * missed runs before writes can hit the default partition.
     */
    private int futureMonths = 3;

    /**
     * Physical removal of whole month partitions. Disable to keep every month
     * indefinitely (for example when an external archive still reads them).
     */
    private boolean dropOldPartitions = true;

    /**
     * How many months to keep before a partition becomes eligible for removal.
     * Messages additionally honour {@code app.chat.retention-days}.
     */
    private int retentionMonths = 24;
}
