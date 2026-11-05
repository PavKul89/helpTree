package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Builds the user rating and the {@code user_rating_stats} projection from the
 * single source of truth: non-deleted help records and non-deleted reviews.
 *
 * <p>Nothing is incremented here. Every call recomputes the whole projection,
 * which makes the operation naturally idempotent: repeating a confirmation,
 * cancellation, review creation or review moderation cannot double-count an
 * act. The same facts feed achievements, global statistics and the graph.</p>
 *
 * <p>Formula ("hybrid 60/40"):</p>
 * <pre>
 *   base                             3.0
 *   confirmed help                  +0.10 per act, at most +1.5
 *   cancelled help                  -0.10 per act, at most -1.0
 *   reviews       (avg - 3.0) * 0.5 * min(1, reviews / 5), at most +/-1.0
 *   debt above threshold            -0.3
 *   active block                    -0.7
 *   clamp 1..5 and round to 0.1
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RatingCalculationService {

    private static final double BASE_RATING = 3.0;
    private static final double HELP_BONUS_PER_ACT = 0.10;
    private static final double HELP_BONUS_MAX = 1.5;
    private static final double CANCEL_PENALTY_PER_ACT = 0.10;
    private static final double CANCEL_PENALTY_MAX = 1.0;
    private static final double REVIEW_WEIGHT = 0.5;
    private static final double REVIEW_CONFIDENCE_FLOOR = 5.0;
    private static final double REVIEW_CONTRIBUTION_MAX = 1.0;
    private static final double DEBT_PENALTY = 0.3;
    private static final double BLOCK_PENALTY = 0.7;
    private static final String RATING_CACHE = "userRating";
    private static final String PUBLIC_PROFILE_CACHE = "publicUserProfile";

    private static final List<HelpStatus> ACTIVE_STATUSES =
            List.of(HelpStatus.ACCEPTED, HelpStatus.COMPLETED);

    private final UserRatingStatsRepository statsRepository;
    private final RatingHistoryRepository historyRepository;
    private final HelpRepository helpRepository;
    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final EconomyConfig economyConfig;
    private final CacheManager cacheManager;

    @Transactional
    public Double calculateUserRating(Long userId) {
        return calculateUserRating(userId, "Автоматический пересчет");
    }

    /**
     * Recomputes the projection inside the caller's transaction (when present).
     * Callers must flush pending help/review changes first so the aggregates
     * observe the new facts.
     */
    @Transactional
    public Double calculateUserRating(Long userId, String reason) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("Расчет рейтинга пропущен: пользователь {} не найден", userId);
            return null;
        }

        statsRepository.insertIfAbsent(userId);
        UserRatingStats stats = statsRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Не удалось создать статистику рейтинга для пользователя " + userId));

        long totalHelpRecords = helpRepository.countAllByHelperId(userId);
        long confirmedHelps = helpRepository.countByHelperIdAndStatus(userId, HelpStatus.CONFIRMED);
        long cancelledHelps = helpRepository.countByHelperIdAndStatus(userId, HelpStatus.CANCELLED);
        long activeHelps = helpRepository.countByHelperIdAndStatusIn(userId, ACTIVE_STATUSES);
        long receivedHelps = helpRepository.countByReceiverIdAndStatus(userId, HelpStatus.CONFIRMED);

        long reviewCount = reviewRepository.countActiveByToUserId(userId);
        Double averageReviewRating = reviewRepository.findAverageRatingByToUserId(userId);

        Double oldRating = stats.getCurrentRating();
        double newRating = calculateRating(user, confirmedHelps, cancelledHelps,
                reviewCount, averageReviewRating);

        stats.setTotalHelpsGiven(totalHelpRecords);
        stats.setSuccessfulHelps(confirmedHelps);
        stats.setCancelledHelps(cancelledHelps);
        stats.setActiveHelpsGiven(activeHelps);
        stats.setTotalHelpsReceived(receivedHelps);
        stats.setReviewCount(reviewCount);
        stats.setAverageReviewRating(averageReviewRating);
        stats.setCurrentRating(newRating);
        stats.setRatingTrend(determineTrend(oldRating, newRating));
        stats.setLastCalculated(LocalDateTime.now());
        statsRepository.save(stats);

        user.setRating(newRating);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        boolean changed = oldRating == null || Double.compare(oldRating, newRating) != 0;
        if (changed) {
            historyRepository.save(RatingHistory.builder()
                    .userId(userId)
                    .oldRating(oldRating)
                    .newRating(newRating)
                    .calculatedAt(LocalDateTime.now())
                    .reason(reason)
                    .ratingComponents(buildComponents(
                            confirmedHelps, cancelledHelps, activeHelps,
                            reviewCount, averageReviewRating, user))
                    .build());
        }

        evictRatingCacheAfterCommit(userId);

        log.info("Рейтинг пользователя {} пересчитан из фактов: {} -> {} (confirmed={}, cancelled={}, active={}, reviews={})",
                userId, oldRating, newRating, confirmedHelps, cancelledHelps, activeHelps, reviewCount);
        return newRating;
    }

    private double calculateRating(User user,
                                   long confirmedHelps,
                                   long cancelledHelps,
                                   long reviewCount,
                                   Double averageReviewRating) {
        double rating = BASE_RATING;

        rating += Math.min(HELP_BONUS_MAX, confirmedHelps * HELP_BONUS_PER_ACT);
        rating -= Math.min(CANCEL_PENALTY_MAX, cancelledHelps * CANCEL_PENALTY_PER_ACT);

        if (reviewCount > 0 && averageReviewRating != null) {
            double confidence = Math.min(1.0, reviewCount / REVIEW_CONFIDENCE_FLOOR);
            double contribution = (averageReviewRating - BASE_RATING) * REVIEW_WEIGHT * confidence;
            rating += Math.max(-REVIEW_CONTRIBUTION_MAX,
                    Math.min(REVIEW_CONTRIBUTION_MAX, contribution));
        }

        int debt = user.getDebtCount() != null ? user.getDebtCount() : 0;
        if (debt > economyConfig.getDebtBlockThreshold()) {
            rating -= DEBT_PENALTY;
        }

        if (isActivelyBlocked(user)) {
            rating -= BLOCK_PENALTY;
        }

        rating = Math.max(1.0, Math.min(5.0, rating));
        return Math.round(rating * 10.0) / 10.0;
    }

    private boolean isActivelyBlocked(User user) {
        if (user.getBlockedUntil() != null) {
            return user.getBlockedUntil().isAfter(LocalDateTime.now());
        }
        return user.getBlockedAt() != null;
    }

    private String determineTrend(Double oldRating, double newRating) {
        if (oldRating == null) {
            return "STABLE";
        }
        if (newRating > oldRating + 0.05) {
            return "UP";
        }
        if (newRating < oldRating - 0.05) {
            return "DOWN";
        }
        return "STABLE";
    }

    private String buildComponents(long confirmedHelps,
                                   long cancelledHelps,
                                   long activeHelps,
                                   long reviewCount,
                                   Double averageReviewRating,
                                   User user) {
        int debt = user.getDebtCount() != null ? user.getDebtCount() : 0;
        return String.format(Locale.ROOT,
                "{\"confirmed\":%d,\"cancelled\":%d,\"active\":%d,\"reviews\":%d,"
                        + "\"averageReview\":%s,\"debt\":%d,\"blocked\":%s}",
                confirmedHelps, cancelledHelps, activeHelps, reviewCount,
                averageReviewRating != null ? averageReviewRating : "null",
                debt, isActivelyBlocked(user));
    }

    /**
     * Evicting before commit would let a concurrent reader cache the old row
     * again. The eviction therefore runs after the surrounding transaction
     * commits; outside a transaction it runs immediately.
     */
    private void evictRatingCacheAfterCommit(Long userId) {
        Runnable evict = () -> {
            Cache ratingCache = cacheManager.getCache(RATING_CACHE);
            if (ratingCache != null) {
                ratingCache.evict(userId);
            }
            // The public profile embeds rating and debt counters, so it becomes
            // stale at exactly the same moment as the rating projection.
            Cache profileCache = cacheManager.getCache(PUBLIC_PROFILE_CACHE);
            if (profileCache != null) {
                profileCache.evict(userId);
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }
}
