package org.example.helptreeservice.enums;

/**
 * Business events that can produce a notification. The value is stored in
 * {@code notifications.type} and in {@code notification_preferences.type}, so
 * it is part of the public API and must stay stable.
 */
public enum NotificationType {
    /** The receiver sees a new response to their help request. */
    NEW_HELP_RESPONSE,
    /** The helper sees that their response was accepted. */
    HELP_ACCEPTED,
    /** The receiver sees that the helper marked the help as completed. */
    HELP_COMPLETED,
    /** The helper sees that the receiver confirmed the help. */
    HELP_CONFIRMED,
    /** A participant sees that the help was cancelled. */
    HELP_CANCELLED,
    /** A user received a review. */
    NEW_REVIEW,
    /** A moderator sees a newly created report. */
    NEW_REPORT,
    /** The reporter sees that their report was accepted or resolved. */
    REPORT_ACCEPTED,
    /** The reporter sees that their report was rejected or linked as duplicate. */
    REPORT_REJECTED,
    /** The owner of moderated content sees the applied action. */
    MODERATION_ACTION,
    /** The account owner sees that their account was blocked. */
    ACCOUNT_BLOCKED,
    /** The account owner sees that their account was unblocked. */
    ACCOUNT_UNBLOCKED,
    /** A positive wallet delta was applied. */
    COINS_EARNED,
    /** A negative wallet delta was applied. */
    COINS_SPENT,
    /** The user earned an achievement. */
    ACHIEVEMENT_EARNED
}
