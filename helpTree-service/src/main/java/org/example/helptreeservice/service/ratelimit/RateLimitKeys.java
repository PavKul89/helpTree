package org.example.helptreeservice.service.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Builds stable, bounded database keys. Keys that do not fit into the
 * {@code VARCHAR(255)} column are hashed; login keys are always hashed so that
 * no email address is persisted in the rate-limit tables.
 */
public final class RateLimitKeys {

    private static final int MAX_LENGTH = 255;
    private static final HexFormat HEX = HexFormat.of();

    private RateLimitKeys() {
    }

    public static String normalize(String raw) {
        String cleaned = raw == null ? "" : raw.replace('\r', ' ').replace('\n', ' ');
        if (cleaned.length() <= MAX_LENGTH) {
            return cleaned;
        }
        return "sha256:" + hash(cleaned);
    }

    public static String hashed(String... parts) {
        return hash(String.join("\u001f", parts));
    }

    private static String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandatory in every JVM; this cannot happen in practice.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
