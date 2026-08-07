package org.example.helptreeservice.config;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MinioHealthIndicator implements HealthIndicator {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Override
    public Health health() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (exists) {
                return Health.up()
                        .withDetail("bucket", bucket)
                        .withDetail("status", "accessible")
                        .build();
            }
            return Health.down()
                    .withDetail("bucket", bucket)
                    .withDetail("status", "bucket not found")
                    .build();
        } catch (Exception e) {
            log.error("MinIO health check failed: {}", e.getMessage());
            return Health.down()
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}
