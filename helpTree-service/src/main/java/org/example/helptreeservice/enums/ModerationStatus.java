package org.example.helptreeservice.enums;

/**
 * Moderator-controlled visibility of user generated content.
 *
 * <p>It is intentionally separate from the author's {@code deleted} flag:
 * "my author removed this" and "a moderator hid/removed this" are different
 * facts and must remain distinguishable in the database.</p>
 */
public enum ModerationStatus {
    ACTIVE,
    HIDDEN,
    REMOVED
}
