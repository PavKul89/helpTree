package org.example.helptreeservice.repository;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.enums.ModerationStatus;
import org.example.helptreeservice.enums.PostStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostSpecificationTest {

    @Mock
    private Root<Post> root;
    @Mock
    private CriteriaQuery<?> query;
    @Mock
    private CriteriaBuilder cb;
    @Mock
    private Path<Object> deletedPath;
    @Mock
    private Path<Object> statusPath;
    @Mock
    private Path<Object> moderationPath;
    @Mock
    private Predicate deletedPredicate;
    @Mock
    private Predicate statusPredicate;
    @Mock
    private Predicate cancelledPredicate;
    @Mock
    private Predicate moderationPredicate;
    @Mock
    private Predicate combinedPredicate;

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Predicate> apply(PostStatus status, boolean excludeCancelled, boolean excludeModerated) {
        when(root.get("deleted")).thenReturn((Path) deletedPath);
        when(root.get("status")).thenReturn((Path) statusPath);
        when(cb.equal(deletedPath, false)).thenReturn(deletedPredicate);
        if (status != null) {
            when(cb.equal(statusPath, status)).thenReturn(statusPredicate);
        }
        if (excludeCancelled) {
            when(cb.notEqual(statusPath, PostStatus.CANCELLED)).thenReturn(cancelledPredicate);
        }
        if (excludeModerated) {
            when(root.get("moderationStatus")).thenReturn((Path) moderationPath);
            when(cb.equal(moderationPath, ModerationStatus.ACTIVE)).thenReturn(moderationPredicate);
        }
        when(cb.and(any(Predicate[].class))).thenReturn(combinedPredicate);

        Specification<Post> spec = PostSpecification.filter(
                null, status, null, null, null, null, excludeCancelled, excludeModerated);

        assertSame(combinedPredicate, spec.toPredicate(root, query, cb));

        ArgumentCaptor<Predicate[]> captor = ArgumentCaptor.forClass(Predicate[].class);
        verify(cb).and(captor.capture());
        return Arrays.asList(captor.getValue());
    }

    @Test
    void publicFilter_shouldHideCancelledWhenExplicitlyRequested() {
        List<Predicate> predicates = apply(PostStatus.CANCELLED, true, true);

        assertTrue(predicates.contains(deletedPredicate));
        assertTrue(predicates.contains(statusPredicate));
        assertTrue(predicates.contains(cancelledPredicate));
        assertTrue(predicates.contains(moderationPredicate));
        verify(cb).notEqual(statusPath, PostStatus.CANCELLED);
    }

    @Test
    void ownerFilter_shouldAllowCancelledWhenExplicitlyRequested() {
        List<Predicate> predicates = apply(PostStatus.CANCELLED, false, false);

        assertTrue(predicates.contains(deletedPredicate));
        assertTrue(predicates.contains(statusPredicate));
        assertFalse(predicates.contains(cancelledPredicate));
        assertFalse(predicates.contains(moderationPredicate));
        verify(cb, never()).notEqual(statusPath, PostStatus.CANCELLED);
    }
}
