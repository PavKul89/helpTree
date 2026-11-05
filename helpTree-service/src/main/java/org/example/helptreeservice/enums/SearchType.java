package org.example.helptreeservice.enums;

/**
 * Categories that actually exist in the service. There is no separate
 * publication entity: a user-facing publication is a {@link #POST}.
 */
public enum SearchType {
    ALL,
    POST,
    USER,
    REVIEW
}
