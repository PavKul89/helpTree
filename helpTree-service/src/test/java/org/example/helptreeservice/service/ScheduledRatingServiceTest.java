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
    @Mock private RatingHistoryCleanupService historyCleanupService;

    private ScheduledRatingService service;

    @BeforeEach
    void setUp() {
        service = new ScheduledRatingService(statsRepository, calculationService, historyCleanupService);
        ReflectionTestUtils.setField(service, "batchSize", 2);
        ReflectionTestUtils.setField(service, "historyRetentionDays", 365);
        ReflectionTestUtils.setField(service, "historyCleanupBatchSize", 1000);
        ReflectionTestUtils.setField(service, "historyMaxBatchesPerRun", 10);
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

    @Test
    void cleanupShouldDeleteInBatchesUntilBelowBatchSize() {
        when(historyCleanupService.deleteBatch(any(), eq(1000)))
                .thenReturn(1000, 1000, 10);

        service.cleanupOldHistory();

        verify(historyCleanupService, times(3)).deleteBatch(any(), eq(1000));
    }

    @Test
    void cleanupShouldStopAtTheConfiguredBatchCap() {
        ReflectionTestUtils.setField(service, "historyMaxBatchesPerRun", 2);
        when(historyCleanupService.deleteBatch(any(), eq(1000))).thenReturn(1000);

        service.cleanupOldHistory();

        verify(historyCleanupService, times(2)).deleteBatch(any(), eq(1000));
    }

    @Test
    void cleanupShouldNeverDeleteInsideTheRetentionWindow() {
        when(historyCleanupService.deleteBatch(any(), eq(1000))).thenReturn(0);

        service.cleanupOldHistory();

        org.mockito.ArgumentCaptor<java.time.LocalDateTime> cutoff =
                org.mockito.ArgumentCaptor.forClass(java.time.LocalDateTime.class);
        verify(historyCleanupService).deleteBatch(cutoff.capture(), eq(1000));
        org.junit.jupiter.api.Assertions.assertTrue(
                cutoff.getValue().isBefore(java.time.LocalDateTime.now().minusDays(364)));
    }
}
