package org.example.helptreeservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.websocket")
public class WebSocketProperties {

    private String allowedOrigins = "http://localhost:3000,http://localhost:3001,http://localhost:5173";

    /**
     * When enabled, the simple in-memory broker is replaced by a STOMP relay
     * (RabbitMQ, ActiveMQ, etc.) so several application instances can share
     * subscriptions without sticky sessions.
     */
    private boolean relayEnabled = false;
    private String relayHost = "localhost";
    private int relayPort = 61613;
    private String clientLogin = "guest";
    private String clientPasscode = "guest";
    private String systemLogin = "guest";
    private String systemPasscode = "guest";
    private long heartbeatSeconds = 10;
}
