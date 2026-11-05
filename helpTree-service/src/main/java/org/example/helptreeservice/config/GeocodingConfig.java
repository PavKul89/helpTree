package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Nominatim access is deliberately asynchronous. The request path only reads a
 * durable cache and enqueues missing cities; the scheduled worker performs the
 * rate-limited HTTP call outside of user requests.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.geocoding")
public class GeocodingConfig {

    private boolean enabled = true;
    private String baseUrl = "https://nominatim.openstreetmap.org/search";
    private String userAgent = "HelpTree/1.0 (Java Backend)";
    private String countryCode = "by";
    private long workerIntervalMs = 1100;
    private int batchSize = 1;
    private int maxAttempts = 5;
    /** A PROCESSING row older than this is considered abandoned and retried. */
    private int staleMinutes = 30;
}
