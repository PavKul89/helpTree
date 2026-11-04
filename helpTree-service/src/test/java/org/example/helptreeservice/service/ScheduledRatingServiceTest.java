package org.example.helptreeservice.service;

import org.example.helptreeservice.entity.UserRatingStats;
import org.example.helptreeservice.repository.UserRatingStatsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduledRatingServiceTest {

    @Mock private UserRatingStatsRepository statsRepository;
    @Mock private RatingCalculationService calculationService;

    private ScheduledRatingService service;

    @BeforeEach
    void setUp() {
        service = new ScheduledRatingService(statsRepository, calculationService);
        ReflectionTestUtils.setField(service, "batchSize", 2);
    }

    private UserRatingStats stats(long id, long userId) {
        return UserRatingStats.builder().id(id).userId(userId).build();
    }

    @Test
    void shouldPageWithIdCursorSoRecalculatedRowsCannotBeSkipped() {
        UserRatingStats first = stats(10L, 1L);
        UserRatingStats second = stats(20L, 2L);
        when(statsRepository.findStaleStatsAfter(any(), eq(0L), any(Pageable.class)))
                .thenReturn(List.of(first, second));
        when(statsRepository.findStaleStatsAfter(any(), eq(20L), any(Pageable.class)))
                .thenReturn(List.of());

        service.recalculateAllRatings();

        verify(calculationService).calculateUserRating(1L);
        verify(calculationService).calculateUserRating(2L);
        // The cursor must move to the last processed id, never an offset.
        verify(statsRepository).findStaleStatsAfter(any(), eq(20L), any(Pageable.class));
    }

    @Test
    void shouldContinueAfterAFailingRow() {
        UserRatingStats first = stats(10L, 1L);
        UserRatingStats second = stats(20L, 2L);
        when(statsRepository.findStaleStatsAfter(any(), eq(0L), any(Pageable.class)))
                .thenReturn(List.of(first, second));
        when(statsRepository.findStaleStatsAfter(any(), eq(20L), any(Pageable.class)))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("boom"))
                .when(calculationService).calculateUserRating(1L);

        service.recalculateAllRatings();

        verify(calculationService).calculateUserRating(2L);
    }

    @Test
    void shouldStopWhenBatchIsEmpty() {
        when(statsRepository.findStaleStatsAfter(any(), eq(0L), any(Pageable.class)))
                .thenReturn(List.of());

        service.recalculateAllRatings();

        verifyNoInteractions(calculationService);
        verify(statsRepository, times(1))
                .findStaleStatsAfter(any(), eq(0L), any(Pageable.class));
    }
}
