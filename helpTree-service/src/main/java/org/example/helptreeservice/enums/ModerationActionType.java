package org.example.helptreeservice.enums;

/**
 * Sanction/decision applied by a moderator. Every value here is also accepted
 * by the {@code moderation_actions.action_type} constraint in V51.
 */
public enum ModerationActionType {
    WARN,
    MUTE,
    UNMUTE,
    BAN,
    UNBAN,
    /** Legacy value kept for historical rows; new code uses HIDE/REMOVE_CONTENT. */
    DELETE_CONTENT,
    HIDE_CONTENT,
    REMOVE_CONTENT,
    RESTORE_CONTENT,
    RESOLVE_REPORT,
    DISMISS_REPORT,
    LINK_DUPLICATE,
    ESCALATE,
    OTHER
}
