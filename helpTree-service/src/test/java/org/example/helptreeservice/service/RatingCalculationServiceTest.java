package org.example.helptreeservice.service;

import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.entity.RatingHistory;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.entity.UserRatingStats;
import org.example.helptreeservice.enums.HelpStatus;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.RatingHistoryRepository;
import org.example.helptreeservice.repository.ReviewRepository;
import org.example.helptreeservice.repository.UserRatingStatsRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingCalculationServiceTest {

    @Mock private UserRatingStatsRepository statsRepository;
    @Mock private RatingHistoryRepository historyRepository;
    @Mock private HelpRepository helpRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private UserRepository userRepository;
    @Mock private CacheManager cacheManager;
    @Mock private Cache cache;

    private RatingCalculationService service;

    @BeforeEach
    void setUp() {
        service = new RatingCalculationService(
                statsRepository,
                historyRepository,
                helpRepository,
                reviewRepository,
                userRepository,
                new EconomyConfig(),
                cacheManager
        );
    }

    private User user(long debt, LocalDateTime blockedUntil) {
        User user = new User();
        user.setId(1L);
        user.setName("User 1");
        user.setDeleted(false);
        user.setDebtCount((int) debt);
        user.setBlockedUntil(blockedUntil);
        return user;
    }

    private UserRatingStats stats(double currentRating) {
        return UserRatingStats.builder()
                .id(1L)
                .userId(1L)
                .currentRating(currentRating)
                .lastCalculated(LocalDateTime.now().minusDays(1))
                .build();
    }

    private void facts(long records,
                       long confirmed,
                       long cancelled,
                       long active,
                       long received,
                       long reviews,
                       Double averageReview) {
        when(helpRepository.countAllByHelperId(1L)).thenReturn(records);
        when(helpRepository.countByHelperIdAndStatus(1L, HelpStatus.CONFIRMED)).thenReturn(confirmed);
        when(helpRepository.countByHelperIdAndStatus(1L, HelpStatus.CANCELLED)).thenReturn(cancelled);
        when(helpRepository.countByHelperIdAndStatusIn(1L, List.of(HelpStatus.ACCEPTED, HelpStatus.COMPLETED)))
                .thenReturn(active);
        when(helpRepository.countByReceiverIdAndStatus(1L, HelpStatus.CONFIRMED)).thenReturn(received);
        when(reviewRepository.countActiveByToUserId(1L)).thenReturn(reviews);
        when(reviewRepository.findAverageRatingByToUserId(1L)).thenReturn(averageReview);
    }

    @Test
    void shouldApplyApprovedHybridFormula() {
        User user = user(0, null);
        UserRatingStats stats = stats(3.0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(statsRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(stats));
        // 10 confirmed = +1.0, 2 cancelled = -0.2, 5 reviews avg 5 = +1.0
        facts(14, 10, 2, 2, 4, 5, 5.0);

        Double rating = service.calculateUserRating(1L);

        assertEquals(4.8, rating);
        assertEquals(4.8, stats.getCurrentRating());
        assertEquals(4.8, user.getRating());
        assertEquals(14L, stats.getTotalHelpsGiven());
        assertEquals(10L, stats.getSuccessfulHelps());
        assertEquals(2L, stats.getCancelledHelps());
        assertEquals(2L, stats.getActiveHelpsGiven());
        assertEquals(4L, stats.getTotalHelpsReceived());
        assertEquals(5L, stats.getReviewCount());
        assertEquals(5.0, stats.getAverageReviewRating());
    }

    @Test
    void shouldCapHelpBonusAndCancelPenalty() {
        User user = user(0, null);
        UserRatingStats stats = stats(3.0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(statsRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(stats));
        // 100 confirmed -> capped +1.5; 50 cancelled -> capped -1.0
        facts(150, 100, 50, 0, 0, 0, null);

        Double rating = service.calculateUserRating(1L);

        assertEquals(3.5, rating);
    }

    @Test
    void shouldScaleReviewContributionByConfidence() {
        User user = user(0, null);
        UserRatingStats stats = stats(3.0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(statsRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(stats));
        // One 5-star review: (5-3)*0.5*(1/5) = +0.2
        facts(0, 0, 0, 0, 0, 1, 5.0);

        Double rating = service.calculateUserRating(1L);

        assertEquals(3.2, rating);
    }

    @Test
    void shouldApplyDebtAndActiveBlockPenalties() {
        User user = user(6, LocalDateTime.now().plusDays(3));
        user.setBlockedAt(LocalDateTime.now().minusDays(1));
        UserRatingStats stats = stats(3.0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(statsRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(stats));
        facts(0, 0, 0, 0, 0, 0, null);

        Double rating = service.calculateUserRating(1L);

        // 3.0 - 0.3 debt - 0.7 block
        assertEquals(2.0, rating);
    }

    @Test
    void shouldClampAndRoundToTenth() {
        User user = user(0, null);
        UserRatingStats stats = stats(3.0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(statsRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(stats));
        // 4.5 + max review +1.0 = 5.5 -> clamp to 5.0
        facts(45, 45, 0, 0, 0, 10, 5.0);

        Double rating = service.calculateUserRating(1L);

        assertEquals(5.0, rating);
    }

    @Test
    void shouldBeIdempotentAndRecordHistoryOnlyWhenRatingChanges() {
        User user = user(0, null);
        UserRatingStats stats = stats(3.0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(statsRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(stats));
        facts(10, 10, 0, 0, 0, 0, null);

        service.calculateUserRating(1L, "confirm");
        service.calculateUserRating(1L, "confirm");

        ArgumentCaptor<RatingHistory> history = ArgumentCaptor.forClass(RatingHistory.class);
        verify(historyRepository, times(1)).save(history.capture());
        assertEquals(3.0, history.getValue().getOldRating());
        assertEquals(4.0, history.getValue().getNewRating());
        assertEquals("confirm", history.getValue().getReason());
        // The projection is not double-counted on a repeated confirmation.
        assertEquals(10L, stats.getSuccessfulHelps());
        assertEquals(4.0, stats.getCurrentRating());
    }

    @Test
    void shouldEvictRatingCache() {
        User user = user(0, null);
        UserRatingStats stats = stats(3.0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(statsRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(stats));
        when(cacheManager.getCache("userRating")).thenReturn(cache);
        facts(1, 1, 0, 0, 0, 0, null);

        service.calculateUserRating(1L);

        verify(cache).evict(1L);
    }

    @Test
    void shouldSkipUnknownUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertNull(service.calculateUserRating(99L));
        verify(statsRepository, never()).findByUserIdForUpdate(any());
    }
}
