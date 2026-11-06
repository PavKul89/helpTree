package org.example.helptreeservice.enums;

/**
 * Controls who may see a profile in public directories.
 *
 * <ul>
 *   <li>{@link #PUBLIC} — visible to anonymous visitors and authenticated users.</li>
 *   <li>{@link #REGISTERED} — visible only to authenticated community members.</li>
 *   <li>{@link #PRIVATE} — hidden from every directory; only the owner and admins see it.</li>
 * </ul>
 */
public enum ProfileVisibility {
    PUBLIC,
    REGISTERED,
    PRIVATE
}
