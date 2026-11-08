package org.example.helptreeservice.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.enums.FeatureFlag;
import org.example.helptreeservice.exception.FeatureDisabledException;
import org.springframework.stereotype.Component;

/**
 * Single server-side gate for managed toggles.
 *
 * <p>Checks live in services, not controllers: a scheduled job or an internal
 * caller must observe exactly the same switch as an HTTP request. The
 * notification flag is the AND of the product toggle and the existing global
 * notification kill switch, so both old and new configuration keep working.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FeatureFlags {

    private final FeatureFlagsProperties properties;
    private final NotificationProperties notificationProperties;

    public boolean isEnabled(FeatureFlag feature) {
        if (feature == null) {
            return true;
        }
        return switch (feature) {
            case REPORTS -> properties.isReports();
            case NOTIFICATIONS -> properties.isNotifications() && notificationProperties.isEnabled();
            case USER_BLOCKS -> properties.isUserBlocks();
            case MODERATION -> properties.isModeration();
        };
    }

    /**
     * Fails closed for a user-facing operation. Throws a mapped exception
     * instead of returning a partial or silently different result.
     */
    public void requireEnabled(FeatureFlag feature) {
        if (!isEnabled(feature)) {
            throw new FeatureDisabledException(feature);
        }
    }

    /**
     * Used by background workers: a disabled feature means "nothing to do",
     * not an error that would fill the log on every tick.
     */
    public boolean isDisabled(FeatureFlag feature) {
        return !isEnabled(feature);
    }
}
