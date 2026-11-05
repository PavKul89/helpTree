package org.example.helptreeservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Durable cache/queue for city geocoding. One row per normalized city, so a
 * city is resolved at most once (or a bounded number of failed attempts) no
 * matter how many users/posts reference it.
 */
@Entity
@Table(name = "geocoding_cache")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeocodingCacheEntry {

    public static final String PENDING = "PENDING";
    public static final String PROCESSING = "PROCESSING";
    public static final String FOUND = "FOUND";
    public static final String FAILED = "FAILED";

    @Id
    @Column(name = "city_key", length = 255)
    private String cityKey;

    @Column(name = "query_city", nullable = false, length = 255)
    private String queryCity;

    private Double latitude;
    private Double longitude;

    @Column(nullable = false, length = 20)
    private String status = PENDING;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
