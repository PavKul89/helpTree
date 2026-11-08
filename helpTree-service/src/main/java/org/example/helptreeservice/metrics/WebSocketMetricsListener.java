package org.example.helptreeservice.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/**
 * Converts Spring's STOMP session events into connection and subscription
 * metrics.
 *
 * <p>Only counts are recorded. Destinations are not used as tags because a
 * destination can contain an identifier; the number of subscriptions is enough
 * to detect a fan-out or reconnect storm.</p>
 */
@Component
@RequiredArgsConstructor
public class WebSocketMetricsListener {

    private final HelpTreeMetrics metrics;

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        metrics.webSocketConnected();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        metrics.webSocketDisconnected();
        metrics.webSocketDisconnectEvent();
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        // The event is published for every SUBSCRIBE frame, including retries.
        metrics.webSocketSubscribed();
    }
}
