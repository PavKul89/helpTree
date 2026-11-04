package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.RatingResponse;
import org.example.helptreeservice.entity.RatingHistory;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.entity.UserRatingStats;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.RatingHistoryRepository;
import org.example.helptreeservice.repository.UserRatingStatsRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RatingService {

    private final UserRatingStatsRepository statsRepository;
    private final RatingHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final RatingCalculationService calculationService;

    @Cacheable(value = "userRating", key = "#userId")
    public RatingResponse getUserRating(Long userId) {
        log.info("Получение рейтинга для пользователя ID: {}", userId);

        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        UserRatingStats stats = statsRepository.findByUserId(userId)
                .orElseGet(() -> defaultStats(userId));

        return buildRatingResponse(user, stats);
    }

    public RatingResponse recalculateUserRating(Long userId) {
        log.info("Принудительный пересчет рейтинга для пользователя ID: {}", userId);

        calculationService.calculateUserRating(userId, "Принудительный пересчет");

        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        UserRatingStats stats = statsRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Статистика не найдена"));

        return buildRatingResponse(user, stats);
    }

    /**
     * Recomputes every affected user from the facts. Users are processed in a
     * deterministic order so two concurrent confirmations that touch the same
     * pair cannot deadlock on the {@code user_rating_stats} row locks.
     *
     * <p>Callers must run inside their business transaction and flush the help
     * or review change first; the calculation joins that transaction.</p>
     */
    public void recalculateUsers(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        new TreeSet<>(userIds.stream().filter(Objects::nonNull).collect(Collectors.toSet()))
                .forEach(calculationService::calculateUserRating);
    }

    public Page<RatingResponse> getTopRatedUsers(Pageable pageable) {
        log.info("Получение топа пользователей по рейтингу");

        Page<UserRatingStats> topStats = statsRepository.findTopRated(pageable);

        List<Long> userIds = topStats.getContent().stream()
                .map(UserRatingStats::getUserId)
                .collect(Collectors.toList());

        Map<Long, User> userMap = userRepository.findAllById(userIds).stream()
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .collect(Collectors.toMap(User::getId, user -> user));

        List<RatingResponse> content = topStats.getContent().stream()
                .map(stats -> {
                    User user = userMap.get(stats.getUserId());
                    if (user == null) {
                        log.warn("Пользователь ID {} не найден или удалён, пропущен в топе рейтингов", stats.getUserId());
                        return null;
                    }
                    return buildRatingResponse(user, stats);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, topStats.getTotalElements());
    }

    public Page<RatingHistory> getRatingHistory(Long userId, Pageable pageable) {
        log.info("Получение истории рейтинга для пользователя ID: {}", userId);
        if (!userRepository.existsByIdAndDeletedFalse(userId)) {
            throw new NotFoundException("Пользователь не найден");
        }
        return historyRepository.findByUserIdOrderByCalculatedAtDesc(userId, pageable);
    }

    private UserRatingStats defaultStats(Long userId) {
        log.info("Создание дефолтной статистики для пользователя ID: {}", userId);
        return UserRatingStats.builder()
                .userId(userId)
                .totalHelpsGiven(0L)
                .totalHelpsReceived(0L)
                .successfulHelps(0L)
                .cancelledHelps(0L)
                .activeHelpsGiven(0L)
                .reviewCount(0L)
                .currentRating(3.0)
                .ratingTrend("STABLE")
                .lastCalculated(LocalDateTime.now())
                .build();
    }

    private RatingResponse buildRatingResponse(User user, UserRatingStats stats) {
        Long successful = nonNull(stats.getSuccessfulHelps());
        Long cancelled = nonNull(stats.getCancelledHelps());
        Long active = nonNull(stats.getActiveHelpsGiven());
        Long reviewCount = nonNull(stats.getReviewCount());
        Double averageReview = stats.getAverageReviewRating();

        Map<String, Double> components = new HashMap<>();
        components.put("currentRating", stats.getCurrentRating());
        components.put("successRate", calculateSuccessRate(stats));
        components.put("avgResponseTime", stats.getAverageResponseTime());
        components.put("confirmedHelps", successful.doubleValue());
        components.put("cancelledHelps", cancelled.doubleValue());
        components.put("activeHelps", active.doubleValue());
        components.put("reviewCount", reviewCount.doubleValue());
        components.put("averageReviewRating", averageReview);

        return RatingResponse.builder()
                .userId(user.getId())
                .userName(user.getName())
                .overallRating(stats.getCurrentRating())
                .ratingComponents(components)
                .level(determineRatingLevel(stats.getCurrentRating()))
                .calculatedAt(stats.getLastCalculated())
                .totalHelps(successful)
                .totalHelpRecords(nonNull(stats.getTotalHelpsGiven()))
                .activeHelps(active)
                .cancelledHelps(cancelled)
                .totalReceivedHelps(nonNull(stats.getTotalHelpsReceived()))
                .reviewCount(reviewCount)
                .averageReviewRating(averageReview)
                .successRate(calculateSuccessRate(stats))
                .build();
    }

    private long nonNull(Long value) {
        return value != null ? value : 0L;
    }

    private String determineRatingLevel(Double rating) {
        if (rating == null) return "AVERAGE";
        if (rating >= 4.5) return "EXCELLENT";
        if (rating >= 3.5) return "GOOD";
        if (rating >= 2.5) return "AVERAGE";
        if (rating >= 1.5) return "POOR";
        return "BAD";
    }

    private Double calculateSuccessRate(UserRatingStats stats) {
        long successful = nonNull(stats.getSuccessfulHelps());
        long cancelled = nonNull(stats.getCancelledHelps());
        long total = successful + cancelled;
        if (total == 0) return 0.0;
        return (double) successful / total * 100;
    }
}
