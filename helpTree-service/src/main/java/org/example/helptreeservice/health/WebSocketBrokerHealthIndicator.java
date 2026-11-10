package org.example.helptreeservice.health;

import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.WebSocketProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Reports whether the configured STOMP relay is reachable. With the in-memory
 * simple broker there is nothing to probe, so the component is always UP and
 * says so explicitly.
 */
@Component("webSocketBroker")
@RequiredArgsConstructor
public class WebSocketBrokerHealthIndicator implements HealthIndicator {

    private final WebSocketProperties properties;

    @Override
    public Health health() {
        if (!properties.isRelayEnabled()) {
            return Health.up()
                    .withDetail("mode", "simple")
                    .build();
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(properties.getRelayHost(),
                    properties.getRelayPort()), 2_000);
            return Health.up()
                    .withDetail("mode", "relay")
                    .withDetail("host", properties.getRelayHost())
                    .withDetail("port", properties.getRelayPort())
                    .build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("mode", "relay")
                    .withDetail("host", properties.getRelayHost())
                    .withDetail("port", properties.getRelayPort())
                    .withDetail("error", e.getClass().getSimpleName())
                    .build();
        }
    }
}
