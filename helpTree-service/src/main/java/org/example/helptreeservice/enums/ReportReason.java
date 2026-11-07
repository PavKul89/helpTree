package org.example.helptreeservice.enums;

/**
 * Reasons accepted by the report form. The database column is intentionally
 * wider, so a future client can add a reason without a migration; the enum only
 * rejects values the server does not understand.
 */
public enum ReportReason {
    SPAM,
    ABUSE,
    HARASSMENT,
    INAPPROPRIATE,
    FRAUD,
    IMPERSONATION,
    OTHER
}
