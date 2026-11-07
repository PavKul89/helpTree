package org.example.helptreeservice.repository;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.enums.ModerationStatus;
import org.example.helptreeservice.enums.PostStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PostSpecification {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private PostSpecification() {}

    public static Specification<Post> filter(final Long userId,
                                             final PostStatus status,
                                             final String title,
                                             final String authorName,
                                             final String category,
                                             final String city,
                                             final boolean excludeCancelled,
                                             final boolean excludeModerated) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Фильтруем мягко удалённые
            predicates.add(cb.equal(root.get("deleted"), false));

            if (excludeModerated) {
                predicates.add(cb.equal(root.get("moderationStatus"), ModerationStatus.ACTIVE));
            }

            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // Отменённые посты скрыты от публичного просмотра даже при явном status=CANCELLED
            if (excludeCancelled) {
                predicates.add(cb.notEqual(root.get("status"), PostStatus.CANCELLED));
            }

            if (StringUtils.hasText(title)) {
                String searchTerm = likePattern(title);
                if (searchTerm != null) {
                    Predicate titleMatch = cb.like(cb.lower(root.get("title")), searchTerm);
                    Predicate descMatch = cb.like(cb.lower(root.get("description")), searchTerm);
                    Predicate authorMatch = cb.like(cb.lower(root.get("user").get("name")), searchTerm);
                    Predicate categoryMatch = cb.like(cb.lower(root.get("category")), searchTerm);
                    Predicate cityMatch = cb.like(cb.lower(root.get("user").get("city")), searchTerm);
                    predicates.add(cb.or(titleMatch, descMatch, authorMatch, categoryMatch, cityMatch));
                }
            }

            if (StringUtils.hasText(authorName)) {
                String searchTerm = likePattern(authorName);
                if (searchTerm != null) {
                    predicates.add(cb.like(cb.lower(root.get("authorName")), searchTerm));
                }
            }

            if (StringUtils.hasText(category)) {
                predicates.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase(Locale.ROOT)));
            }

            if (StringUtils.hasText(city)) {
                String searchTerm = likePattern(city);
                if (searchTerm != null) {
                    predicates.add(cb.like(cb.lower(root.get("user").get("city")), searchTerm));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Server-side map filter. Coordinates are mandatory (a marker without a
     * point is useless); cancelled posts are always hidden. When both a centre
     * and a radius are supplied the bounding box narrows the indexed scan and
     * the haversine predicate keeps only points inside the circle.
     */
    public static Specification<Post> mapFilter(final PostStatus status,
                                                final String category,
                                                final String city,
                                                final String search,
                                                final Double minLat,
                                                final Double minLng,
                                                final Double maxLat,
                                                final Double maxLng,
                                                final Double centerLat,
                                                final Double centerLng,
                                                final Double radiusKm) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.or(
                    cb.equal(root.get("deleted"), false),
                    cb.isNull(root.get("deleted"))));
            predicates.add(cb.equal(root.get("moderationStatus"), ModerationStatus.ACTIVE));
            predicates.add(cb.isNotNull(root.get("latitude")));
            predicates.add(cb.isNotNull(root.get("longitude")));
            predicates.add(cb.notEqual(root.get("status"), PostStatus.CANCELLED));

            PostStatus effectiveStatus = status != null ? status : PostStatus.OPEN;
            if (effectiveStatus != PostStatus.CANCELLED) {
                predicates.add(cb.equal(root.get("status"), effectiveStatus));
            }

            if (StringUtils.hasText(category)) {
                predicates.add(cb.equal(cb.lower(root.get("category")),
                        category.trim().toLowerCase(Locale.ROOT)));
            }

            if (StringUtils.hasText(city)) {
                String searchTerm = likePattern(city);
                if (searchTerm != null) {
                    predicates.add(cb.like(cb.lower(root.get("user").get("city")), searchTerm));
                }
            }

            if (StringUtils.hasText(search)) {
                String searchTerm = likePattern(search);
                if (searchTerm != null) {
                    predicates.add(cb.or(
                            cb.like(cb.lower(root.get("title")), searchTerm),
                            cb.like(cb.lower(root.get("description")), searchTerm),
                            cb.like(cb.lower(root.get("category")), searchTerm),
                            cb.like(cb.lower(root.get("user").get("city")), searchTerm)));
                }
            }

            if (minLat != null && maxLat != null) {
                predicates.add(cb.between(root.get("latitude"), minLat, maxLat));
            }
            if (minLng != null && maxLng != null) {
                predicates.add(cb.between(root.get("longitude"), minLng, maxLng));
            }

            if (centerLat != null && centerLng != null && radiusKm != null && radiusKm > 0) {
                Expression<Double> lat = root.get("latitude");
                Expression<Double> lng = root.get("longitude");
                Expression<Double> centerLatRad = cb.function("radians", Double.class, cb.literal(centerLat));
                Expression<Double> centerLngRad = cb.function("radians", Double.class, cb.literal(centerLng));
                Expression<Double> pointLatRad = cb.function("radians", Double.class, lat);
                Expression<Double> pointLngRad = cb.function("radians", Double.class, lng);

                Expression<Double> cosines = cb.prod(
                        cb.prod(
                                cb.function("cos", Double.class, centerLatRad),
                                cb.function("cos", Double.class, pointLatRad)),
                        cb.function("cos", Double.class, cb.diff(pointLngRad, centerLngRad)));
                Expression<Double> sines = cb.prod(
                        cb.function("sin", Double.class, centerLatRad),
                        cb.function("sin", Double.class, pointLatRad));
                Expression<Double> cosine = cb.sum(cosines, sines);
                Expression<Double> clamped = cb.function("least", Double.class,
                        cb.literal(1.0),
                        cb.function("greatest", Double.class, cb.literal(-1.0), cosine));
                Expression<Double> centralAngle = cb.function("acos", Double.class, clamped);

                predicates.add(cb.lessThanOrEqualTo(centralAngle, radiusKm / EARTH_RADIUS_KM));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String likePattern(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        // Match SearchService: a literal substring search must not let the user
        // turn the pattern into "match everything" with SQL LIKE wildcards.
        String literal = value.trim()
                .toLowerCase(Locale.ROOT)
                .replace("%", "")
                .replace("_", "");
        return literal.isEmpty() ? null : "%" + literal + "%";
    }
}
