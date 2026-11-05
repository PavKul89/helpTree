package org.example.helptreeservice.enums;

import java.util.Optional;

/**
 * Formats accepted by the image pipeline. Detection is based on magic bytes
 * only; the multipart {@code Content-Type} and filename extension are ignored.
 */
public enum ImageFormat {
    JPEG("JPEG", "image/jpeg", ".jpg", true),
    PNG("PNG", "image/png", ".png", true),
    GIF("GIF", "image/gif", ".gif", true),
    /**
     * Detected only so the API can return a precise error. Java ImageIO has no
     * built-in WebP reader, and a silent external plugin would make decoding
     * behaviour depend on the deployment image.
     */
    WEBP("WEBP", "image/webp", ".webp", false);

    private final String readerFormat;
    private final String contentType;
    private final String extension;
    private final boolean supported;

    ImageFormat(String readerFormat, String contentType, String extension, boolean supported) {
        this.readerFormat = readerFormat;
        this.contentType = contentType;
        this.extension = extension;
        this.supported = supported;
    }

    public String getReaderFormat() {
        return readerFormat;
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return extension;
    }

    public boolean isSupported() {
        return supported;
    }

    public static Optional<ImageFormat> detect(byte[] data) {
        if (data == null || data.length < 3) {
            return Optional.empty();
        }
        int b0 = data[0] & 0xFF;
        int b1 = data[1] & 0xFF;
        int b2 = data[2] & 0xFF;

        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) {
            return Optional.of(JPEG);
        }
        if (data.length >= 8
                && (data[0] & 0xFF) == 0x89
                && data[1] == 'P' && data[2] == 'N' && data[3] == 'G'
                && (data[4] & 0xFF) == 0x0D && (data[5] & 0xFF) == 0x0A
                && (data[6] & 0xFF) == 0x1A && (data[7] & 0xFF) == 0x0A) {
            return Optional.of(PNG);
        }
        if (data.length >= 6
                && data[0] == 'G' && data[1] == 'I' && data[2] == 'F'
                && data[3] == '8'
                && (data[4] == '7' || data[4] == '9')
                && data[5] == 'a') {
            return Optional.of(GIF);
        }
        if (data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }
}
