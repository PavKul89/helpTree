package org.example.helptreeservice.repository;

import jakarta.persistence.criteria.Predicate;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.Role;
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
        return (root, queryBuilder, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.or(
                    cb.equal(root.get("deleted"), false),
                    cb.isNull(root.get("deleted"))));

            if (StringUtils.hasText(query)) {
                String pattern = likePattern(query);
                if (pattern != null) {
                    List<Predicate> matches = new ArrayList<>();
                    matches.add(cb.like(cb.lower(root.get("name")), pattern));
                    matches.add(cb.like(cb.lower(root.get("city")), pattern));
                    if (includeEmail) {
                        matches.add(cb.like(cb.lower(root.get("email")), pattern));
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
