package org.example.helptreeservice.service.notification;

/** A failure that retrying cannot fix; the outbox event moves straight to DEAD. */
public class PermanentDeliveryException extends RuntimeException {

    public PermanentDeliveryException(String message) {
        super(message);
    }
}
