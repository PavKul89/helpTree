package org.example.helptreeservice.config;

import org.example.helptreeservice.enums.FeatureFlag;
import org.example.helptreeservice.exception.FeatureDisabledException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureFlagsTest {

    private final FeatureFlagsProperties properties = new FeatureFlagsProperties();
    private final NotificationProperties notificationProperties = new NotificationProperties();
    private final FeatureFlags flags = new FeatureFlags(properties, notificationProperties);

    @Test
    void featuresAreEnabledByDefault() {
        for (FeatureFlag feature : FeatureFlag.values()) {
            assertTrue(flags.isEnabled(feature), feature.name());
        }
    }

    @Test
    void disablingAFeatureFailsClosedWithTheSpecificFeature() {
        properties.setReports(false);

        FeatureDisabledException error = assertThrows(FeatureDisabledException.class,
                () -> flags.requireEnabled(FeatureFlag.REPORTS));

        assertEquals(FeatureFlag.REPORTS, error.getFeature());
        assertTrue(error.getMessage().contains(FeatureFlag.REPORTS.getTitle()));
        assertTrue(flags.isDisabled(FeatureFlag.REPORTS));
        assertTrue(flags.isEnabled(FeatureFlag.NOTIFICATIONS));
    }

    @Test
    void notificationsRequireBothTheProductToggleAndTheGlobalKillSwitch() {
        properties.setNotifications(true);
        notificationProperties.setEnabled(false);
        assertFalse(flags.isEnabled(FeatureFlag.NOTIFICATIONS));

        properties.setNotifications(false);
        notificationProperties.setEnabled(true);
        assertFalse(flags.isEnabled(FeatureFlag.NOTIFICATIONS));

        properties.setNotifications(true);
        assertTrue(flags.isEnabled(FeatureFlag.NOTIFICATIONS));
    }

    @Test
    void nullFeatureIsTreatedAsAlwaysEnabled() {
        assertTrue(flags.isEnabled(null));
    }
}
