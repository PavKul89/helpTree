package org.example.helptreeservice.enums;

/**
 * Stable action names written to {@code admin_audit_log}. Keeping them in one
 * enum prevents typo-driven gaps in the audit trail.
 */
public enum AdminAuditAction {
    USER_CREATED,
    USER_INVITED,
    USER_ROLE_CHANGED,
    USER_DELETED,
    USER_RESTORED,
    USER_ANONYMIZED,
    USER_DATA_EXPORTED,

    POST_DELETED,
    POST_RESTORED,
    POST_STATUS_CHANGED,

    REVIEW_DELETED,

    REPORT_ASSIGNED,
    REPORT_UPDATED,
    REPORT_STATUS_CHANGED,
    MODERATION_ACTION,

    COINS_ADJUSTED,
    RATING_CHANGED,

    SESSION_REVOKED,
    SESSION_REVOKED_ALL,

    HELP_OVERRIDE,

    BULK_OPERATION
}
