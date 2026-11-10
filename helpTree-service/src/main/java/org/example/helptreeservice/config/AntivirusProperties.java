package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ClamAV INSTREAM settings for upload scanning.
 *
 * <p>Scanning is disabled by default. When enabled it is fail-closed: an upload
 * is rejected if the scanner is unreachable or answers with anything other than
 * a clean result.</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.antivirus")
public class AntivirusProperties {

    private boolean enabled = false;
    private String host = "localhost";
    private int port = 3310;
    private int connectTimeoutMillis = 2_000;
    private int readTimeoutMillis = 10_000;

    /** Maximum bytes sent in one INSTREAM chunk. */
    private int chunkSize = 64 * 1024;
}
