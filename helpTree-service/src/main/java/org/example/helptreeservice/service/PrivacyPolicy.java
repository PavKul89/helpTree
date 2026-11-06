package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.enums.ActivityType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.Set;

/**
 * Single source of truth for every "who may see what" decision.
 *
 * <p>The system has two persisted roles, {@code USER} and {@code ADMIN}. There
 * is no separate {@code MODERATOR} role: moderation is an administrative
 * capability. If such a role is introduced later it must be added here, not in
 * individual controllers.</p>
 *
 * <p>Audiences:</p>
 * <ul>
 *   <li>{@link Audience#PUBLIC} - anonymous or another signed-in user;</li>
 *   <li>{@link Audience#OWNER} - the user the data belongs to;</li>
 *   <li>{@link Audience#PARTICIPANT} - a helper or receiver of a specific help
 *       act (the act itself is never part of a public projection);</li>
 *   <li>{@link Audience#ADMIN} - administrative/moderation access.</li>
 * </ul>
 *
 * <p>All checks are performed on the backend. The frontend may hide controls,
 * but it is never the authority: for example, a blocked post author is rejected
 * by {@code HelpService.acceptHelp} even though the public post projection no
 * longer carries a block flag.</p>
 */
@Service
@RequiredArgsConstructor
public class PrivacyPolicy {

    /**
     * Public coordinates are rounded to roughly a one-kilometre grid. Exact
     * coordinates are only returned to the owner of the post and to admins.
     */
    public static final int PUBLIC_COORDINATE_DECIMALS = 2;

    private static final Set<ActivityType> PUBLIC_ACTIVITY_TYPES =
            EnumSet.of(ActivityType.HELP_GIVEN,
                    ActivityType.HELP_RECEIVED,
                    ActivityType.POST_CREATED,
                    ActivityType.ACHIEVEMENT_EARNED);

    private final AuthorizationService authorizationService;

    public enum Audience {
        PUBLIC,
        OWNER,
        PARTICIPANT,
        ADMIN
    }

    /**
     * Audience of the viewer for data owned by {@code ownerUserId}.
     * A {@code null} owner means a global/public resource (for example the
     * global help graph).
     */
    public Audience audienceFor(Long ownerUserId) {
        AuthorizationService.UserContext current = authorizationService.getCurrentUser();
        if (current == null) {
            return Audience.PUBLIC;
        }
        if (isAdmin(current)) {
            return Audience.ADMIN;
        }
        if (ownerUserId != null && current.getUserId().equals(ownerUserId)) {
            return Audience.OWNER;
        }
        return Audience.PUBLIC;
    }

    /** Audience for a help act: its two participants, otherwise public/admin. */
    public Audience audienceForHelp(Long helperId, Long receiverId) {
        AuthorizationService.UserContext current = authorizationService.getCurrentUser();
        if (current == null) {
            return Audience.PUBLIC;
        }
        if (isAdmin(current)) {
            return Audience.ADMIN;
        }
        if (current.getUserId().equals(helperId) || current.getUserId().equals(receiverId)) {
            return Audience.PARTICIPANT;
        }
        return Audience.PUBLIC;
    }

    /** Owner and admin may see the complete post projection. */
    public boolean canViewFullPost(Long authorId) {
        Audience audience = audienceFor(authorId);
        return audience == Audience.OWNER || audience == Audience.ADMIN;
    }

    /** Owner and admin may modify the post. Same audience as the full view. */
    public boolean canManagePost(Long authorId) {
        return canViewFullPost(authorId);
    }

    /** Only owner/admin may see exact coordinates; everybody else gets rounded. */
    public boolean canViewExactCoordinates(Long authorId) {
        return canViewFullPost(authorId);
    }

    /** Only owner/admin may see the exact boost expiry. */
    public boolean canViewBoostExpiry(Long authorId) {
        return canViewFullPost(authorId);
    }

    /** Debt-block events and participant names are private activity. */
    public boolean canViewPrivateActivity(Long profileUserId) {
        return canViewFullPost(profileUserId);
    }

    /**
     * The graph is public, but participant-specific fields (debt counter,
     * concrete post) are only returned to the graph owner and admins.
     */
    public boolean canViewPrivateGraph(Long rootUserId) {
        return canViewFullPost(rootUserId);
    }

    /** Rating calculation history contains internal components. */
    public boolean canViewRatingHistory(Long userId) {
        return canViewFullPost(userId);
    }

    /** General feed: only admins may see cancelled posts. */
    public boolean canViewAllPostStatuses(Long authorId) {
        if (authorId == null) {
            return isAdmin(authorizationService.getCurrentUser());
        }
        return canViewFullPost(authorId);
    }

    public boolean canViewHelp(Long helperId, Long receiverId) {
        Audience audience = audienceForHelp(helperId, receiverId);
        return audience == Audience.PARTICIPANT || audience == Audience.ADMIN;
    }

    public boolean isModerator() {
        return isAdmin(authorizationService.getCurrentUser());
    }

    public boolean isPublicActivity(ActivityType type) {
        return PUBLIC_ACTIVITY_TYPES.contains(type);
    }

    public Set<ActivityType> publicActivityTypes() {
        return Set.copyOf(PUBLIC_ACTIVITY_TYPES);
    }

    /**
     * Rounds a coordinate for public responses. {@code null} stays {@code null}
     * so "no location" is never confused with the zero meridian.
     */
    public Double publicCoordinate(Double value) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value)
                .setScale(PUBLIC_COORDINATE_DECIMALS, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private boolean isAdmin(AuthorizationService.UserContext current) {
        return current != null && "ADMIN".equals(current.getRole());
    }
}
