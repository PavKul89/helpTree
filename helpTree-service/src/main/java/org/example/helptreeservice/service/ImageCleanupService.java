package org.example.helptreeservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.ImageCleanupProperties;
import org.example.helptreeservice.entity.ImageObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Bounded sweep for images that no longer have a reason to exist.
 *
 * <p>Three independent sources are handled:</p>
 * <ol>
 *   <li>ownership rows already marked DELETED — remove the object, then the
 *       row;</li>
 *   <li>active rows older than the TTL that no avatar/post references — mark
 *       deleted atomically, then remove the object;</li>
 *   <li>objects in the bucket with no ownership row at all — usually a crash
 *       between upload and registration.</li>
 * </ol>
 *
 * <p>Every batch and every run is capped. The scheduled method returns quickly
 * on an empty bucket and never holds a transaction across the Silo call.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ImageCleanupService {

    private final ImageObjectService imageObjectService;
    private final ImageService imageService;
    private final MinioClient siloClient;
    private final ImageCleanupProperties properties;
    private final MeterRegistry meterRegistry;

    @Value("${silo.bucket}")
    private String bucket;

    @Scheduled(cron = "${upload.cleanup.cron:0 30 3 * * *}")
    public void scheduledCleanup() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            cleanup();
        } catch (Exception e) {
            log.error("Ошибка очистки изображений", e);
        }
    }

    public CleanupSummary cleanup() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime orphanCutoff = now.minusHours(Math.max(1L, properties.getOrphanTtlHours()));

        int deletedRows = cleanupDeletedRows(now);
        int orphanRows = cleanupUnreferencedRows(orphanCutoff);
        int bucketObjects = properties.isBucketScanEnabled()
                ? cleanupBucketObjects(orphanCutoff)
                : 0;

        if (deletedRows + orphanRows + bucketObjects > 0) {
            log.info("Очистка изображений: tombstone={}, orphan={}, bucket={}",
                    deletedRows, orphanRows, bucketObjects);
        }
        return new CleanupSummary(deletedRows, orphanRows, bucketObjects);
    }

    private int cleanupDeletedRows(LocalDateTime cutoff) {
        int removed = 0;
        for (int batch = 0; batch < properties.getMaxBatchesPerRun(); batch++) {
            List<ImageObject> candidates = imageObjectService.findDeletedBefore(
                    cutoff, properties.getBatchSize());
            if (candidates.isEmpty()) {
                break;
            }

            int progress = 0;
            for (ImageObject object : candidates) {
                if (!imageService.tryRemoveObject(object.getObjectKey())) {
                    recordFailure("deleted");
                    continue;
                }
                if (imageObjectService.deleteDeletedRow(object.getId()) > 0) {
                    removed++;
                    progress++;
                }
            }
            if (progress == 0) {
                // The same unremovable object would be retried forever within
                // this run; leave it for the next scheduled attempt.
                break;
            }
        }
        recordDeleted("deleted", removed);
        return removed;
    }

    private int cleanupUnreferencedRows(LocalDateTime cutoff) {
        int removed = 0;
        for (int batch = 0; batch < properties.getMaxBatchesPerRun(); batch++) {
            List<ImageObject> candidates = imageObjectService.findUnreferencedActive(
                    cutoff, properties.getBatchSize());
            if (candidates.isEmpty()) {
                break;
            }

            int progress = 0;
            for (ImageObject object : candidates) {
                int marked = imageObjectService.markUnreferencedDeleted(object.getId(), cutoff);
                if (marked == 0) {
                    // A post/profile claimed it between the query and the
                    // conditional update; keep the bytes.
                    continue;
                }
                if (!imageService.tryRemoveObject(object.getObjectKey())) {
                    recordFailure("orphan");
                    continue;
                }
                if (imageObjectService.deleteDeletedRow(object.getId()) > 0) {
                    removed++;
                    progress++;
                }
            }
            if (progress == 0) {
                break;
            }
        }
        recordDeleted("orphan", removed);
        return removed;
    }

    private int cleanupBucketObjects(LocalDateTime cutoff) {
        int removed = 0;
        int scanned = 0;
        int maxObjects = Math.max(1, properties.getBucketScanMaxObjects());

        try {
            Iterable<Result<Item>> items = siloClient.listObjects(ListObjectsArgs.builder()
                    .bucket(bucket)
                    .recursive(true)
                    .maxKeys(Math.min(properties.getBatchSize(), maxObjects))
                    .build());

            for (Result<Item> result : items) {
                if (scanned >= maxObjects) {
                    break;
                }
                Item item = result.get();
                if (item == null || item.isDir() || item.objectName() == null) {
                    continue;
                }
                scanned++;

                if (item.lastModified() == null
                        || !item.lastModified().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                        .isBefore(cutoff)) {
                    continue;
                }
                // A row can appear between list and lookup only for a fresh
                // upload, and fresh objects are excluded by the TTL above.
                if (imageObjectService.hasOwnershipRow(item.objectName())) {
                    continue;
                }
                if (imageService.tryRemoveObject(item.objectName())) {
                    removed++;
                } else {
                    recordFailure("bucket");
                }
            }
        } catch (Exception e) {
            log.warn("Не удалось просканировать bucket {}: {}", bucket, e.getMessage());
            recordFailure("bucket");
        }

        if (removed > 0) {
            log.info("Очистка bucket: удалено {} объектов без строки владения", removed);
        }
        recordDeleted("bucket", removed);
        return removed;
    }

    private void recordDeleted(String source, int count) {
        if (count <= 0) {
            return;
        }
        Counter.builder("helptree.images.cleanup.deleted")
                .description("Image objects removed by the lifecycle sweep")
                .tag("source", source)
                .register(meterRegistry)
                .increment(count);
    }

    private void recordFailure(String source) {
        Counter.builder("helptree.images.cleanup.failures")
                .description("Image cleanup operations that will be retried")
                .tag("source", source)
                .register(meterRegistry)
                .increment();
    }

    public record CleanupSummary(int deletedRows, int orphanRows, int bucketObjects) {
    }
}
