package org.example.helptreeservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Managed feature toggles ({@code app.features.*}).
 *
 * <p>Defaults are deliberately {@code true}: a missing environment variable
 * never silently disables a feature. An operator disables a feature explicitly,
 * and the affected API then fails closed with {@code 503 FEATURE_DISABLED}.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.features")
public class FeatureFlagsProperties {

    private boolean reports = true;
    private boolean notifications = true;
    private boolean userBlocks = true;
    private boolean moderation = true;
}
