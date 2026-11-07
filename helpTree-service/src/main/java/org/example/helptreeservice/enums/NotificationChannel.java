package org.example.helptreeservice.enums;

/**
 * Delivery channel for a notification.
 *
 * <p>{@link #IN_APP} is always persisted as a canonical notification row and is
 * pushed to connected clients over the private STOMP queue. {@link #EMAIL} and
 * {@link #TELEGRAM} are delivered through the durable outbox after the creating
 * transaction commits.</p>
 */
public enum NotificationChannel {
    IN_APP,
    EMAIL,
    TELEGRAM
}
