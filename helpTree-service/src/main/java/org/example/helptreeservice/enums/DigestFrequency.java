package org.example.helptreeservice.enums;

/**
 * Delivery cadence for notification digests. {@link #INSTANT} keeps the current
 * real-time behaviour; {@link #OFF} disables digest delivery entirely.
 */
public enum DigestFrequency {
    INSTANT,
    DAILY,
    WEEKLY,
    OFF
}
