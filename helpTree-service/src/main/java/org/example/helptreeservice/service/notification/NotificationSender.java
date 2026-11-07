package org.example.helptreeservice.service.notification;

import org.example.helptreeservice.enums.NotificationChannel;

/**
 * External delivery transport for one channel. {@code IN_APP} is not a transport:
 * it is persisted as the canonical notification row and pushed over WebSocket.
 */
public interface NotificationSender {

    NotificationChannel channel();

    /** False when the transport is not configured on this instance. */
    boolean isAvailable();

    /**
     * Sends the notification. Returns {@code false} for any transient failure so
     * the outbox can retry with backoff; permanent failures are reported by
     * throwing {@link PermanentDeliveryException}.
     */
    boolean send(NotificationDelivery delivery);
}
