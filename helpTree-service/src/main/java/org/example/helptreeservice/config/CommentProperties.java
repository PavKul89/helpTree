package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.comments")
public class CommentProperties {

    /** Absolute limit; the DTO annotation uses the same upper bound. */
    private int maxLength = 1000;

    /**
     * Root comments are depth 0. Replies to them are depth 1 and so on. The
     * limit keeps the thread readable and the recursive queries bounded.
     */
    private int maxDepth = 3;

    /** Sliding one-minute window per user. */
    private boolean rateLimitEnabled = true;
    private int perMinute = 10;

    private int defaultPageSize = 20;
    private int maxPageSize = 100;
}
