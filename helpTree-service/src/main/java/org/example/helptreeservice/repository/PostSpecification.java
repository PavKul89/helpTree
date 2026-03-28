package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.enums.PostStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public final class PostSpecification {

    private PostSpecification() {}

    public static Specification<Post> filter(final Long userId,
                                             final PostStatus status,
                                             final String title,
                                             final String authorName,
                                             final String category,
                                             final String city) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Фильтруем мягко удалённые
            predicates.add(cb.equal(root.get("deleted"), false));

            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (StringUtils.hasText(title)) {
                String searchTerm = "%" + title.toLowerCase() + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), searchTerm);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), searchTerm);
                Predicate authorMatch = cb.like(cb.lower(root.get("user").get("name")), searchTerm);
                predicates.add(cb.or(titleMatch, descMatch, authorMatch));
            }

            if (StringUtils.hasText(authorName)) {
                predicates.add(cb.like(cb.lower(root.get("authorName")), "%" + authorName.toLowerCase() + "%"));
            }

            if (StringUtils.hasText(category)) {
                predicates.add(cb.equal(root.get("category"), category));
            }

            if (StringUtils.hasText(city)) {
                predicates.add(cb.like(cb.lower(root.get("user").get("city")), "%" + city.toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
