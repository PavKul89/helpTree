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
    USER_DATA_EXPORTED
}
