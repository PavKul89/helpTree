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
                                             final String category) {
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
                predicates.add(cb.like(cb.lower(root.get("title")), "%" + title.toLowerCase() + "%"));
            }

            if (StringUtils.hasText(authorName)) {
                predicates.add(cb.like(cb.lower(root.get("authorName")), "%" + authorName.toLowerCase() + "%"));
            }

            if (StringUtils.hasText(category)) {
                predicates.add(cb.equal(root.get("category"), category));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
