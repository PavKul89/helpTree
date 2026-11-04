package org.example.helptreeservice.controller;

import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.reviews.ReviewResponse;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.ReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminReviewControllerTest {

    @Mock private AuthorizationService authService;
    @Mock private ReviewService reviewService;
    @Mock private PaginationConfig paginationConfig;

    @InjectMocks private AdminReviewController controller;

    @BeforeEach
    void denyByDefault() {
        lenient().when(authService.isAdmin()).thenReturn(false);
    }

    @Test
    void getReviewsShouldForbidNonAdmin() {
        assertThrows(ForbiddenException.class,
                () -> controller.getReviews(null, null, null, null, 0, 20));

        verifyNoInteractions(reviewService);
    }

    @Test
    void getReviewsShouldRejectInvalidRating() {
        when(authService.isAdmin()).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> controller.getReviews(null, 0, null, null, 0, 20));
        assertThrows(BadRequestException.class,
                () -> controller.getReviews(null, 6, null, null, 0, 20));

        verifyNoInteractions(reviewService);
    }

    @Test
    void getReviewsShouldRejectInvertedDateRange() {
        when(authService.isAdmin()).thenReturn(true);
        LocalDateTime from = LocalDateTime.of(2026, 1, 2, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 1, 1, 0, 0);

        assertThrows(BadRequestException.class,
                () -> controller.getReviews(null, null, from, to, 0, 20));

        verifyNoInteractions(reviewService);
    }

    @Test
    void getReviewsShouldRejectInvalidPagination() {
        when(authService.isAdmin()).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> controller.getReviews(null, null, null, null, -1, 20));
        assertThrows(BadRequestException.class,
                () -> controller.getReviews(null, null, null, null, 0, 0));

        verifyNoInteractions(reviewService);
    }

    @Test
    void getReviewsShouldClampPageSizeAndSortNewestFirst() {
        when(authService.isAdmin()).thenReturn(true);
        when(paginationConfig.getMaxPageSize()).thenReturn(100);
        when(reviewService.getAdminReviews(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(Page.empty());

        controller.getReviews(null, null, null, null, 0, 5000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(reviewService).getAdminReviews(isNull(), isNull(), isNull(), isNull(),
                captor.capture());
        assertEquals(100, captor.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC,
                captor.getValue().getSort().getOrderFor("createdAt").getDirection());
    }

    @Test
    void getReviewsShouldPassFiltersThrough() {
        when(authService.isAdmin()).thenReturn(true);
        when(paginationConfig.getMaxPageSize()).thenReturn(100);
        LocalDateTime from = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 2, 1, 0, 0);
        ReviewResponse review = ReviewResponse.builder()
                .id(1L)
                .helpId(2L)
                .postId(3L)
                .toUserId(7L)
                .rating(5)
                .build();
        when(reviewService.getAdminReviews(eq(7L), eq(5), eq(from), eq(to), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review)));

        var response = controller.getReviews(7L, 5, from, to, 1, 20);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getContent().size());
        assertEquals(7L, response.getBody().getContent().get(0).getToUserId());
    }
}
