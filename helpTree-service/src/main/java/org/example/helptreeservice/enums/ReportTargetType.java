package org.example.helptreeservice.enums;

public enum ReportTargetType {
    POST,
    USER,
    COMMENT,
    REVIEW,
    MESSAGE,
    /** Historical only: new reports on HELP/CHAT are rejected. */
    HELP,
    /** Historical only: new reports on HELP/CHAT are rejected. */
    CHAT,
    /** Target of a report-level moderation action; never a report target itself. */
    REPORT
}
