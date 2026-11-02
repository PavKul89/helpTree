package org.example.helptreeservice.exception;

/**
 * Thrown when a user exists but is currently blocked from using the service.
 * It is mapped to HTTP 403 instead of 401 so the client can distinguish it from
 * an invalid token.
 */
public class AccountBlockedException extends RuntimeException {

    public AccountBlockedException(String message) {
        super(message);
    }
}
