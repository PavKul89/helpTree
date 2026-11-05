package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Server-side image limits. The client never gets to relax any of these; the
 * multipart limits in {@code spring.servlet.multipart} are only a first line of
 * defence and the processor repeats the checks on the real bytes.
 */
@Data
@Component
@ConfigurationProperties(prefix = "upload")
public class ImageUploadProperties {

    /** Maximum size of one decoded/uploaded object in bytes. */
    private long maxSize = 10L * 1024 * 1024;

    /** Maximum size of the whole multipart request. */
    private long maxRequestSize = 50L * 1024 * 1024;

    /** Maximum number of files accepted by POST /api/images/multiple. */
    private int maxFiles = 10;

    /** Pre-decode dimension limits: ImageIO never allocates beyond these. */
    private int maxWidth = 6000;
    private int maxHeight = 6000;
    private long maxPixels = 24_000_000L;

    /** JPEG re-encode quality. PNG is re-encoded losslessly. */
    private float jpegQuality = 0.85f;

    /** An animated GIF above this frame count is rejected. */
    private int gifMaxFrames = 200;

    /** Pixel budget across all GIF frames (first frame is the one decoded). */
    private long gifMaxTotalPixels = 100_000_000L;

    /** Per-user active-object quota. */
    private long quotaBytes = 100L * 1024 * 1024;
    private int quotaCount = 500;
}
