package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Bounds for the image lifecycle sweep. Every batch is independently
 * transactional and every run is capped, so the job cannot turn into one long
 * lock/WAL event on a large bucket.
 */
@Data
@Component
@ConfigurationProperties(prefix = "upload.cleanup")
public class ImageCleanupProperties {

    private boolean enabled = true;

    /** Scheduled sweep. */
    private String cron = "0 30 3 * * *";

    /**
     * Objects younger than this are left alone. This covers the gap between
     * putting an object into Silo and committing its ownership row.
     */
    private long orphanTtlHours = 24L;

    /** Rows processed per transaction. */
    private int batchSize = 100;

    /** Maximum number of batches per category per run. */
    private int maxBatchesPerRun = 20;

    /**
     * Best-effort sweep of bucket objects that have no ownership row at all.
     * Disable when another process shares the bucket.
     */
    private boolean bucketScanEnabled = true;

    /** Maximum bucket entries inspected in one run. */
    private int bucketScanMaxObjects = 1000;
}
