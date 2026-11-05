package org.example.helptreeservice.dto.images;

/**
 * Result of decoding and re-encoding an upload. {@code data} is the exact byte
 * sequence that gets stored in Silo, so the recorded size and SHA-256 always
 * describe the object at rest.
 */
public record ProcessedImage(
        byte[] data,
        String contentType,
        String extension,
        int width,
        int height,
        String sha256) {
}
