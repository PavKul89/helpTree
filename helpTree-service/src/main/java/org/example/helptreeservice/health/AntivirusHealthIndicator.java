package org.example.helptreeservice.health;

import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.service.AntivirusScanner;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports ClamAV availability only when scanning is enabled. A disabled scanner
 * is UP with {@code mode=disabled}; that is intentional, because uploads do not
 * depend on it in that mode.
 */
@Component("antivirus")
@RequiredArgsConstructor
public class AntivirusHealthIndicator implements HealthIndicator {

    private final AntivirusScanner scanner;

    @Override
    public Health health() {
        if (!scanner.isEnabled()) {
            return Health.up().withDetail("mode", "disabled").build();
        }
        return scanner.ping()
                ? Health.up().withDetail("mode", "enabled").build()
                : Health.down().withDetail("mode", "enabled").build();
    }
}
