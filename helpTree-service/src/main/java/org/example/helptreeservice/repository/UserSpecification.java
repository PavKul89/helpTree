package org.example.helptreeservice.repository;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.entity.UserBlockRelation;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.UserBlockScope;
import org.example.helptreeservice.enums.UserStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Server-side filters for the paginated user directories. The admin variant may
 * match email; the public variant never exposes or searches it.
 */
public final class UserSpecification {

    private UserSpecification() {}

    public static Specification<User> filter(String query,
                                             Role role,
                                             UserStatus status,
                                             String city,
                                             Boolean blocked,
                                             boolean includeEmail) {
        return filter(query, role, status, city, blocked, includeEmail, false);
    }

    /**
     * Directory filter with an explicit deletion scope. The public/admin
     * directories keep the old overload (active users only); the administrative
     * deleted-accounts view passes {@code deleted=true}.
     */
    public static Specification<User> filter(String query,
                                             Role role,
                                             UserStatus status,
                                             String city,
                                             Boolean blocked,
                                             boolean includeEmail,
                                             Boolean deleted) {
        return (root, queryBuilder, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (Boolean.TRUE.equals(deleted)) {
                predicates.add(cb.equal(root.get("deleted"), true));
            } else {
                predicates.add(cb.or(
                        cb.equal(root.get("deleted"), false),
                        cb.isNull(root.get("deleted"))));
            }

            if (StringUtils.hasText(query)) {
                String pattern = likePattern(query);
                if (pattern != null) {
                    List<Predicate> matches = new ArrayList<>();
                    matches.add(cb.like(cb.lower(root.get("name")), pattern));
                    matches.add(cb.like(cb.lower(root.get("city")), pattern));
                    if (includeEmail) {
                        matches.add(cb.like(cb.lower(root.get("email")), pattern));
                        matches.add(cb.like(cb.lower(root.get("phone")), pattern));
                        Long numericId = parseId(query);
                        if (numericId != null) {
                            matches.add(cb.equal(root.get("id"), numericId));
                        }
                    }
                    predicates.add(cb.or(matches.toArray(new Predicate[0])));
                }
            }

            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (StringUtils.hasText(city)) {
                String pattern = likePattern(city);
                if (pattern != null) {
                    predicates.add(cb.like(cb.lower(root.get("city")), pattern));
                }
            }

            // Mirrors the UI's isUserBlocked: an explicit block timestamp or a
            // still-running blockedUntil window. Filtering stays in SQL so the
            // admin panel can count blocked accounts without listing them.
            if (blocked != null) {
                java.time.LocalDateTime now = java.time.LocalDateTime.now();
                Predicate blockedAt = cb.isNotNull(root.get("blockedAt"));
                Predicate blockedUntil = cb.greaterThan(root.get("blockedUntil"), now);
                if (blocked) {
                    predicates.add(cb.or(blockedAt, blockedUntil));
                } else {
                    predicates.add(cb.and(
                            cb.isNull(root.get("blockedAt")),
                            cb.or(
                                    cb.isNull(root.get("blockedUntil")),
                                    cb.lessThanOrEqualTo(root.get("blockedUntil"), now))));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Hides users that the viewer and the row share a PROFILE block with. The
     * exclusion lives in SQL (a NOT EXISTS subquery) so pagination and total
     * counts stay correct; filtering a loaded page would lie about the size.
     */
    public static Specification<User> excludeProfileBlocks(Long viewerId) {
        return (root, query, cb) -> {
            if (viewerId == null || query == null) {
                return cb.conjunction();
            }
            List<UserBlockScope> scopes = List.of(UserBlockScope.PROFILE, UserBlockScope.ALL);
            Subquery<Long> blockedPair = query.subquery(Long.class);
            Root<UserBlockRelation> relation = blockedPair.from(UserBlockRelation.class);
            Predicate outgoing = cb.and(
                    cb.equal(relation.get("blocker").get("id"), viewerId),
                    cb.equal(relation.get("blocked").get("id"), root.get("id")));
            Predicate incoming = cb.and(
                    cb.equal(relation.get("blocker").get("id"), root.get("id")),
                    cb.equal(relation.get("blocked").get("id"), viewerId));
            blockedPair.select(relation.get("id"))
                    .where(cb.or(outgoing, incoming),
                            cb.or(cb.isNull(relation.get("expiresAt")),
                                    cb.greaterThan(relation.get("expiresAt"),
                                            java.time.LocalDateTime.now())),
                            relation.get("scope").in(scopes));
            return cb.not(cb.exists(blockedPair));
        };
    }

    private static Long parseId(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 18) {
            return null;
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String likePattern(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String literal = value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("%", "")
                .replace("_", "");
        return literal.isEmpty() ? null : "%" + literal + "%";
    }
}
