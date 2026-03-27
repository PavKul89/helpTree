package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.achievement.AchievementDto;
import org.example.helptreeservice.entity.Achievement;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.AchievementType;
import org.example.helptreeservice.enums.HelpStatus;
import org.example.helptreeservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final HelpRepository helpRepository;
    private final PostRepository postRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    @Transactional
    public void checkAndAwardAchievements(User user, Help help) {
        List<AchievementType> newAchievements = new ArrayList<>();

        int helpedCount = user.getHelpedCount();

        // По количеству помощи
        if (helpedCount >= 1 && !hasAchievement(user, AchievementType.FIRST_HELP)) {
            newAchievements.add(AchievementType.FIRST_HELP);
        }
        if (helpedCount >= 5 && !hasAchievement(user, AchievementType.HELPER_5)) {
            newAchievements.add(AchievementType.HELPER_5);
        }
        if (helpedCount >= 10 && !hasAchievement(user, AchievementType.HELPER_10)) {
            newAchievements.add(AchievementType.HELPER_10);
        }
        if (helpedCount >= 25 && !hasAchievement(user, AchievementType.HELPER_25)) {
            newAchievements.add(AchievementType.HELPER_25);
        }
        if (helpedCount >= 50 && !hasAchievement(user, AchievementType.HELPER_50)) {
            newAchievements.add(AchievementType.HELPER_50);
        }
        if (helpedCount >= 100 && !hasAchievement(user, AchievementType.HELPER_100)) {
            newAchievements.add(AchievementType.HELPER_100);
        }

        // По скорости
        if (help.getAcceptedAt() != null) {
            long hoursBetween = java.time.Duration.between(help.getAcceptedAt(), LocalDateTime.now()).toHours();
            if (hoursBetween <= 1 && !hasAchievement(user, AchievementType.FAST_HELP)) {
                newAchievements.add(AchievementType.FAST_HELP);
            }
        }

        int hour = LocalDateTime.now().getHour();
        if ((hour >= 22 || hour < 6) && !hasAchievement(user, AchievementType.NIGHT_OWL)) {
            newAchievements.add(AchievementType.NIGHT_OWL);
        }

        int dayOfWeek = LocalDateTime.now().getDayOfWeek().getValue();
        if ((dayOfWeek == 6 || dayOfWeek == 7) && !hasAchievement(user, AchievementType.WEEKEND_HERO)) {
            newAchievements.add(AchievementType.WEEKEND_HERO);
        }

        // Праздничные
        int month = LocalDateTime.now().getMonthValue();
        int day = LocalDateTime.now().getDayOfMonth();
        
        if (month == 12 && !hasAchievement(user, AchievementType.NEW_YEAR_WIZARD)) {
            newAchievements.add(AchievementType.NEW_YEAR_WIZARD);
        }
        
        if (month == 10 && day == 31 && !hasAchievement(user, AchievementType.HALLOWEEN_HERO)) {
            newAchievements.add(AchievementType.HALLOWEEN_HERO);
        }

        if (user.getBirthDate() != null) {
            int birthMonth = user.getBirthDate().getMonthValue();
            int birthDay = user.getBirthDate().getDayOfMonth();
            if (month == birthMonth && day == birthDay && !hasAchievement(user, AchievementType.BIRTHDAY_HERO)) {
                newAchievements.add(AchievementType.BIRTHDAY_HERO);
            }
        }

        // Зимний (декабрь-февраль)
        if ((month == 12 || month == 1 || month == 2) && !hasAchievement(user, AchievementType.WINTER_HELPER)) {
            newAchievements.add(AchievementType.WINTER_HELPER);
        }

        // Без долгов
        if (helpedCount >= 5 && user.getDebtCount() == 0 && !hasAchievement(user, AchievementType.DEBT_FREE)) {
            newAchievements.add(AchievementType.DEBT_FREE);
        }

        // Мастер баланса
        if (user.getHelpedCount() > user.getDebtCount() && helpedCount >= 3 && !hasAchievement(user, AchievementType.BALANCED)) {
            newAchievements.add(AchievementType.BALANCED);
        }

        // По количеству постов
        long postCount = postRepository.countByUserId(user.getId());
        if (postCount >= 1 && !hasAchievement(user, AchievementType.FIRST_POST)) {
            newAchievements.add(AchievementType.FIRST_POST);
        }
        if (postCount >= 10 && !hasAchievement(user, AchievementType.POSTER_10)) {
            newAchievements.add(AchievementType.POSTER_10);
        }

        // По количеству сообщений (chat achievements)
        long messageCount = messageRepository.countBySenderId(user.getId());
        if (messageCount >= 1 && !hasAchievement(user, AchievementType.FIRST_CHAT)) {
            newAchievements.add(AchievementType.FIRST_CHAT);
        }
        if (messageCount >= 50 && !hasAchievement(user, AchievementType.CHATTER_10)) {
            newAchievements.add(AchievementType.CHATTER_10);
        }

        // VARIETY - помог разным людям
        List<Help> confirmedHelps = helpRepository.findByHelperAndStatus(user, HelpStatus.CONFIRMED);
        Set<Long> uniqueReceivers = new HashSet<>();
        Set<String> uniqueCategories = new HashSet<>();
        for (Help h : confirmedHelps) {
            uniqueReceivers.add(h.getReceiver().getId());
            if (h.getPost() != null && h.getPost().getCategory() != null) {
                uniqueCategories.add(h.getPost().getCategory());
            }
        }
        if (uniqueReceivers.size() >= 10 && !hasAchievement(user, AchievementType.VARIETY)) {
            newAchievements.add(AchievementType.VARIETY);
        }

        // QUICK_FIX - помог в 5 категориях
        if (uniqueCategories.size() >= 5 && !hasAchievement(user, AchievementType.QUICK_FIX)) {
            newAchievements.add(AchievementType.QUICK_FIX);
        }

        // REPUTATION_5 - рейтинг 5.0
        if (user.getRating() != null && user.getRating() >= 5.0 && !hasAchievement(user, AchievementType.REPUTATION_5)) {
            newAchievements.add(AchievementType.REPUTATION_5);
        }

        // CONSISTENT - помогал 3 месяца подряд
        if (confirmedHelps.size() >= 3) {
            Set<String> helpedMonths = new HashSet<>();
            for (Help h : confirmedHelps) {
                if (h.getConfirmedAt() != null) {
                    helpedMonths.add(h.getConfirmedAt().getYear() + "-" + h.getConfirmedAt().getMonthValue());
                }
            }
            if (helpedMonths.size() >= 3 && !hasAchievement(user, AchievementType.CONSISTENT)) {
                newAchievements.add(AchievementType.CONSISTENT);
            }
        }

        // Начисляем достижения
        for (AchievementType type : newAchievements) {
            awardAchievement(user, type);
            log.info("Пользователь {} получил достижение: {}", user.getName(), type.getName());
        }
    }

    private boolean hasAchievement(User user, AchievementType type) {
        return achievementRepository.existsByUserIdAndType(user.getId(), type);
    }

    private void awardAchievement(User user, AchievementType type) {
        Achievement achievement = new Achievement();
        achievement.setUser(user);
        achievement.setType(type);
        achievement.setEarnedAt(LocalDateTime.now());
        achievementRepository.save(achievement);
    }

    public List<AchievementDto> getUserAchievements(Long userId) {
        return achievementRepository.findByUserIdOrderByEarnedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private AchievementDto toDto(Achievement achievement) {
        return AchievementDto.builder()
                .id(achievement.getId())
                .type(achievement.getType().name())
                .emoji(achievement.getType().getEmoji())
                .name(achievement.getType().getName())
                .description(achievement.getType().getDescription())
                .rarity(achievement.getType().getRarity())
                .earnedAt(achievement.getEarnedAt())
                .isEarned(true)
                .build();
    }

    @Transactional(readOnly = true)
    public List<AchievementDto> getAllAchievementTypesWithProgress(Long userId) {
        User user = null;
        if (userId != null) {
            try {
                user = userRepository.findById(userId).orElse(null);
            } catch (Exception e) {
                log.warn("User not found: {}", userId);
            }
        }

        final User finalUser = user;
        return java.util.Arrays.stream(AchievementType.values())
                .map(type -> toDtoWithProgress(type, finalUser))
                .collect(Collectors.toList());
    }

    private AchievementDto toDtoWithProgress(AchievementType type, User user) {
        AchievementDto.AchievementDtoBuilder builder = AchievementDto.builder()
                .type(type.name())
                .emoji(type.getEmoji())
                .name(type.getName())
                .description(type.getDescription())
                .rarity(type.getRarity());

        if (user != null) {
            log.debug("Getting achievements for user: {} with helpedCount: {}, debtCount: {}", 
                user.getId(), user.getHelpedCount(), user.getDebtCount());

            long currentProgress = 0;
            int targetValue = getTargetValue(type);

            switch (type) {
                case SEEDLING:
                    currentProgress = 1;
                    break;
                case FIRST_HELP:
                case HELPER_5:
                case HELPER_10:
                case HELPER_25:
                case HELPER_50:
                case HELPER_100:
                    currentProgress = user.getHelpedCount() != null ? user.getHelpedCount() : 0;
                    break;
                case FAST_HELP:
                case NIGHT_OWL:
                case WEEKEND_HERO:
                case NEW_YEAR_WIZARD:
                case HALLOWEEN_HERO:
                case BIRTHDAY_HERO:
                case WINTER_HELPER:
                case EASTER_BUNNY:
                    currentProgress = 0;
                    targetValue = 1;
                    break;
                case REPUTATION_5:
                    currentProgress = user.getRating() != null ? (int)(user.getRating() * 10) : 0;
                    targetValue = 50;
                    break;
                case CONSISTENT:
                    List<Help> helpsForConsistent = helpRepository.findByHelperAndStatus(user, HelpStatus.CONFIRMED);
                    Set<String> months = new HashSet<>();
                    for (Help h : helpsForConsistent) {
                        if (h.getConfirmedAt() != null) {
                            months.add(h.getConfirmedAt().getYear() + "-" + h.getConfirmedAt().getMonthValue());
                        }
                    }
                    currentProgress = months.size();
                    targetValue = 3;
                    break;
                case QUICK_FIX:
                    List<Help> helpsForQuickFix = helpRepository.findByHelperAndStatus(user, HelpStatus.CONFIRMED);
                    Set<String> categories = new HashSet<>();
                    for (Help h : helpsForQuickFix) {
                        if (h.getPost() != null && h.getPost().getCategory() != null) {
                            categories.add(h.getPost().getCategory());
                        }
                    }
                    currentProgress = categories.size();
                    targetValue = 5;
                    break;
                case FIRST_POST:
                case POSTER_10:
                    currentProgress = (int) postRepository.countByUserId(user.getId());
                    break;
                case FIRST_CHAT:
                case CHATTER_10:
                    currentProgress = (int) messageRepository.countBySenderId(user.getId());
                    break;
                case DEBT_FREE:
                    currentProgress = user.getHelpedCount() != null ? user.getHelpedCount() : 0;
                    targetValue = 5;
                    break;
                case BALANCED:
                    currentProgress = user.getHelpedCount() != null ? user.getHelpedCount() : 0;
                    targetValue = 3;
                    break;
                case VARIETY:
                    List<Help> helps = helpRepository.findByHelperAndStatus(user, HelpStatus.CONFIRMED);
                    Set<Long> uniqueReceivers = new HashSet<>();
                    for (Help h : helps) {
                        uniqueReceivers.add(h.getReceiver().getId());
                    }
                    currentProgress = uniqueReceivers.size();
                    break;
                default:
                    currentProgress = 0;
            }

            boolean hasAchievement = achievementRepository.findFirstByUserIdAndType(user.getId(), type).isPresent();
            boolean isCompleted = currentProgress >= targetValue;
            builder.isEarned(hasAchievement || isCompleted);

            builder.currentProgress((int) Math.min(currentProgress, targetValue));
            builder.targetValue(targetValue);
        }

        return builder.build();
    }

    private int getTargetValue(AchievementType type) {
        switch (type) {
            case SEEDLING: return 1;
            case FIRST_HELP: return 1;
            case HELPER_5: return 5;
            case HELPER_10: return 10;
            case HELPER_25: return 25;
            case HELPER_50: return 50;
            case HELPER_100: return 100;
            case FIRST_POST: return 1;
            case POSTER_10: return 10;
            case FIRST_CHAT: return 1;
            case CHATTER_10: return 50;
            case DEBT_FREE: return 5;
            case BALANCED: return 3;
            case VARIETY: return 10;
            default: return 1;
        }
    }

    public List<AchievementDto> getAllAchievementTypes() {
        return getAllAchievementTypesWithProgress(null);
    }

    @Transactional
    public void awardAchievementOnRegistration(User user) {
        if (!hasAchievement(user, AchievementType.SEEDLING)) {
            awardAchievement(user, AchievementType.SEEDLING);
            log.info("Пользователь {} получил достижение: {}", user.getName(), AchievementType.SEEDLING.getName());
        }
    }
}
