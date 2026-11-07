package org.example.helptreeservice.enums;

/**
 * Lifecycle of a durable outbox event.
 *
 * <ul>
 *   <li>{@link #PENDING} - written but not claimed yet;</li>
 *   <li>{@link #PROCESSING} - claimed by a worker; delivery is in progress;</li>
 *   <li>{@link #PUBLISHED} - delivery succeeded;</li>
 *   <li>{@link #FAILED} - delivery failed and a retry is scheduled;</li>
 *   <li>{@link #DEAD} - retries are exhausted or the failure is permanent.</li>
 * </ul>
 */
public enum OutboxStatus {
    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED,
    DEAD
}
