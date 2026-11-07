package org.example.helptreeservice.enums;

/**
 * Area covered by a user-initiated block.
 *
 * <p>{@link #ALL} is a superset: a row with {@code ALL} applies to every
 * scenario, while a narrower row applies only to its own scenario. The mapping
 * used by enforcement is:</p>
 *
 * <ul>
 *   <li>{@link #MESSAGES} - creating chats and sending messages;</li>
 *   <li>{@link #PROFILE} - public profile, user directory, user search and
 *       reviews;</li>
 *   <li>{@link #HELP_REQUESTS} - responding to a help request;</li>
 *   <li>{@link #ALL} - every scenario above, including comments (comments have
 *       no narrower scope).</li>
 * </ul>
 */
public enum UserBlockScope {
    ALL,
    MESSAGES,
    PROFILE,
    HELP_REQUESTS;

    /**
     * Scopes that satisfy {@code required}. A block row matches when its scope
     * is either the exact scenario or {@link #ALL}.
     */
    public static UserBlockScope[] matching(UserBlockScope required) {
        if (required == null || required == ALL) {
            return new UserBlockScope[] {ALL};
        }
        return new UserBlockScope[] {required, ALL};
    }
}
