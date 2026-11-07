package org.example.helptreeservice.enums;

/**
 * Append-only report history entry types. The database constraint in V51
 * accepts exactly these values.
 */
public enum ReportEventType {
    CREATED,
    ASSIGNED,
    STATUS_CHANGED,
    NOTE,
    ESCALATED,
    RESOLVED,
    REOPENED,
    LINKED_DUPLICATE,
    ACTION_APPLIED
}
