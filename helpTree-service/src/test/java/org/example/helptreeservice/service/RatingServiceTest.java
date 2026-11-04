package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.RatingResponse;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.entity.UserRatingStats;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.RatingHistoryRepository;
import org.example.helptreeservice.repository.UserRatingStatsRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RatingServiceTest {

    @Mock
    private UserRatingStatsRepository statsRepository;
    @Mock
    private RatingHistoryRepository historyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RatingCalculationService calculationService;

    private RatingService ratingService;

    @BeforeEach
    void setUp() {
        ratingService = new RatingService(
                statsRepository,
                historyRepository,
                userRepository,
                calculationService
        );
    }

    private User user(Long id, String name, boolean deleted) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setDeleted(deleted);
        return user;
    }

    private UserRatingStats stats(Long userId, double rating) {
        return UserRatingStats.builder()
                .id(userId)
                .userId(userId)
                .currentRating(rating)
                .totalHelpsGiven(1L)
                .totalHelpsReceived(1L)
                .successfulHelps(1L)
                .cancelledHelps(0L)
                .averageResponseTime(1.0)
                .lastCalculated(LocalDateTime.now())
                .build();
    }

    @Test
    void getUserRating_shouldRejectDeletedUser() {
        when(userRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> ratingService.getUserRating(1L));
        verify(statsRepository, never()).findByUserId(any());
    }

    @Test
    void getRatingHistory_shouldRejectDeletedUser() {
        when(userRepository.existsByIdAndDeletedFalse(1L)).thenReturn(false);

        assertThrows(NotFoundException.class,
                () -> ratingService.getRatingHistory(1L, PageRequest.of(0, 20)));
        verify(historyRepository, never()).findByUserIdOrderByCalculatedAtDesc(any(), any());
    }

    @Test
    void getTopRatedUsers_shouldExcludeDeletedUsersAndNeverReturnNull() {
        Pageable pageable = PageRequest.of(0, 20);
        UserRatingStats first = stats(1L, 4.9);
        UserRatingStats second = stats(2L, 4.5);
        when(statsRepository.findTopRated(pageable))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        when(userRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(user(1L, "Active", false), user(2L, "Deleted", true)));

        Page<RatingResponse> result = ratingService.getTopRatedUsers(pageable);

        assertEquals(1, result.getContent().size());
        assertEquals(1L, result.getContent().get(0).getUserId());
        assertTrue(result.getContent().stream().allMatch(java.util.Objects::nonNull));
    }

    @Test
    void getTopRatedUsers_shouldBeEmptyWhenAllUsersAreDeleted() {
        Pageable pageable = PageRequest.of(0, 20);
        when(statsRepository.findTopRated(pageable))
                .thenReturn(new PageImpl<>(List.of(stats(2L, 4.5)), pageable, 1));
        when(userRepository.findAllById(List.of(2L)))
                .thenReturn(List.of(user(2L, "Deleted", true)));

        Page<RatingResponse> result = ratingService.getTopRatedUsers(pageable);

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void recalculateUsers_shouldRebuildEachAffectedUserOnceInDeterministicOrder() {
        ratingService.recalculateUsers(List.of(3L, 1L, 2L, 1L));

        org.mockito.InOrder inOrder = inOrder(calculationService);
        inOrder.verify(calculationService).calculateUserRating(1L);
        inOrder.verify(calculationService).calculateUserRating(2L);
        inOrder.verify(calculationService).calculateUserRating(3L);
        verify(calculationService, times(3)).calculateUserRating(anyLong());
    }

    @Test
    void recalculateUsers_shouldIgnoreNullOrEmptyInput() {
        ratingService.recalculateUsers(null);
        ratingService.recalculateUsers(List.of());

        verifyNoInteractions(calculationService);
    }
}
