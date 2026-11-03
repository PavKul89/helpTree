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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
                .orElseGet(() -> {
                    log.info("Создание дефолтной статистики для пользователя ID: {}", userId);
                    return UserRatingStats.builder()
                            .userId(userId)
                            .totalHelpsGiven(0L)
                            .totalHelpsReceived(0L)
                            .successfulHelps(0L)
                            .cancelledHelps(0L)
                            .currentRating(3.0)
                            .ratingTrend("STABLE")
                            .lastCalculated(LocalDateTime.now())
                            .build();
                });

        return buildRatingResponse(user, stats);
    }

    @CacheEvict(value = "userRating", key = "#userId")
    public RatingResponse recalculateUserRating(Long userId) {
        log.info("Принудительный пересчет рейтинга для пользователя ID: {}", userId);

        Double newRating = calculationService.calculateUserRating(userId);

        User user = userRepository.findByIdAndDeletedFalse(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        UserRatingStats stats = statsRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Статистика не найдена"));

        return buildRatingResponse(user, stats);
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

    @Transactional
    public void recordSuccessfulHelp(Long helperId, Long receiverId) {
        log.info("Фиксация успешной помощи в статистике: helper={}, receiver={}", helperId, receiverId);

        UserRatingStats helperStats = getStatsForUpdate(helperId);
        helperStats.setTotalHelpsGiven(helperStats.getTotalHelpsGiven() + 1);
        helperStats.setSuccessfulHelps(helperStats.getSuccessfulHelps() + 1);
        helperStats.setLastCalculated(LocalDateTime.now());
        statsRepository.save(helperStats);

        UserRatingStats receiverStats = getStatsForUpdate(receiverId);
        receiverStats.setTotalHelpsReceived(receiverStats.getTotalHelpsReceived() + 1);
        receiverStats.setLastCalculated(LocalDateTime.now());
        statsRepository.save(receiverStats);
    }

    /**
     * A cancellation is not a successful help: the helper's attempt is counted,
     * but the receiver is not credited with a received help.
     */
    @Transactional
    public void recordCancelledHelp(Long helperId) {
        log.info("Фиксация отменённой помощи в статистике: helper={}", helperId);

        UserRatingStats helperStats = getStatsForUpdate(helperId);
        helperStats.setTotalHelpsGiven(helperStats.getTotalHelpsGiven() + 1);
        helperStats.setCancelledHelps(helperStats.getCancelledHelps() + 1);
        helperStats.setLastCalculated(LocalDateTime.now());
        statsRepository.save(helperStats);
    }

    private UserRatingStats getStatsForUpdate(Long userId) {
        statsRepository.insertIfAbsent(userId);
        return statsRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Не удалось создать статистику рейтинга для пользователя " + userId));
    }

    private RatingResponse buildRatingResponse(User user, UserRatingStats stats) {
        Map<String, Double> components = new HashMap<>();
        components.put("currentRating", stats.getCurrentRating());
        components.put("successRate", calculateSuccessRate(stats));
        components.put("avgResponseTime", stats.getAverageResponseTime());

        return RatingResponse.builder()
                .userId(user.getId())
                .userName(user.getName())
                .overallRating(stats.getCurrentRating())
                .ratingComponents(components)
                .level(determineRatingLevel(stats.getCurrentRating()))
                .calculatedAt(stats.getLastCalculated())
                .totalHelps(stats.getTotalHelpsGiven())
                .totalReceivedHelps(stats.getTotalHelpsReceived())
                .successRate(calculateSuccessRate(stats))
                .build();
    }

    private String determineRatingLevel(Double rating) {
        if (rating >= 4.5) return "EXCELLENT";
        if (rating >= 3.5) return "GOOD";
        if (rating >= 2.5) return "AVERAGE";
        if (rating >= 1.5) return "POOR";
        return "BAD";
    }

    private Double calculateSuccessRate(UserRatingStats stats) {
        long total = stats.getSuccessfulHelps() + stats.getCancelledHelps();
        if (total == 0) return 0.0;
        return (double) stats.getSuccessfulHelps() / total * 100;
    }
}
