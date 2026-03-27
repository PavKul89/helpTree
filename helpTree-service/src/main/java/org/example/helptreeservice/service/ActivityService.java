package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.dto.activity.ActivityDto;
import org.example.helptreeservice.dto.achievement.AchievementDto;
import org.example.helptreeservice.entity.Achievement;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.ActivityType;
import org.example.helptreeservice.enums.AchievementType;
import org.example.helptreeservice.repository.AchievementRepository;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private final HelpRepository helpRepository;
    private final PostRepository postRepository;
    private final AchievementRepository achievementRepository;
    private final UserRepository userRepository;

    public List<ActivityDto> getUserActivities(Long userId, int limit) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<ActivityDto> activities = new ArrayList<>();

        activities.addAll(getHelpGivenActivities(user));
        activities.addAll(getHelpReceivedActivities(user));
        activities.addAll(getPostCreatedActivities(user));
        activities.addAll(getAchievementActivities(user));

        return activities.stream()
                .sorted(Comparator.comparing(ActivityDto::getTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private List<ActivityDto> getHelpGivenActivities(User user) {
        List<Help> helps = helpRepository.findByHelperWithDetails(user);
        return helps.stream()
                .filter(help -> help.getStatus() != null)
                .map(help -> ActivityDto.builder()
                        .type(ActivityType.HELP_GIVEN.name())
                        .typeLabel(ActivityType.HELP_GIVEN.getLabel())
                        .emoji(ActivityType.HELP_GIVEN.getEmoji())
                        .title("Помог пользователю")
                        .description(help.getPost() != null ? help.getPost().getTitle() : null)
                        .timestamp(getHelpTimestamp(help))
                        .relatedUserId(help.getReceiver() != null ? help.getReceiver().getId() : null)
                        .relatedUserName(help.getReceiver() != null ? help.getReceiver().getName() : null)
                        .relatedPostId(help.getPost() != null ? help.getPost().getId() : null)
                        .relatedPostTitle(help.getPost() != null ? help.getPost().getTitle() : null)
                        .category(help.getPost() != null ? help.getPost().getCategory() : null)
                        .status(help.getStatus() != null ? help.getStatus().name() : null)
                        .build())
                .collect(Collectors.toList());
    }

    private List<ActivityDto> getHelpReceivedActivities(User user) {
        List<Help> helps = helpRepository.findByReceiverWithDetails(user);
        return helps.stream()
                .filter(help -> help.getStatus() != null)
                .map(help -> ActivityDto.builder()
                        .type(ActivityType.HELP_RECEIVED.name())
                        .typeLabel(ActivityType.HELP_RECEIVED.getLabel())
                        .emoji(ActivityType.HELP_RECEIVED.getEmoji())
                        .title("Получил помощь от " + (help.getHelper() != null ? help.getHelper().getName() : ""))
                        .description(help.getPost() != null ? help.getPost().getTitle() : null)
                        .timestamp(getHelpTimestamp(help))
                        .relatedUserId(help.getHelper() != null ? help.getHelper().getId() : null)
                        .relatedUserName(help.getHelper() != null ? help.getHelper().getName() : null)
                        .relatedPostId(help.getPost() != null ? help.getPost().getId() : null)
                        .relatedPostTitle(help.getPost() != null ? help.getPost().getTitle() : null)
                        .category(help.getPost() != null ? help.getPost().getCategory() : null)
                        .status(help.getStatus() != null ? help.getStatus().name() : null)
                        .build())
                .collect(Collectors.toList());
    }

    private java.time.LocalDateTime getHelpTimestamp(Help help) {
        if (help.getConfirmedAt() != null) return help.getConfirmedAt();
        if (help.getCompletedAt() != null) return help.getCompletedAt();
        if (help.getAcceptedAt() != null) return help.getAcceptedAt();
        return help.getCreatedAt();
    }

    private List<ActivityDto> getPostCreatedActivities(User user) {
        List<Post> posts = postRepository.findByUserId(user.getId());
        return posts.stream()
                .filter(post -> post.getDeleted() == null || !post.getDeleted())
                .map(post -> ActivityDto.builder()
                        .type(ActivityType.POST_CREATED.name())
                        .typeLabel(ActivityType.POST_CREATED.getLabel())
                        .emoji(ActivityType.POST_CREATED.getEmoji())
                        .title("Создал запрос о помощи")
                        .description(post.getTitle())
                        .timestamp(post.getCreatedAt())
                        .relatedPostId(post.getId())
                        .relatedPostTitle(post.getTitle())
                        .category(post.getCategory())
                        .status(post.getStatus() != null ? post.getStatus().name() : null)
                        .build())
                .collect(Collectors.toList());
    }

    private List<ActivityDto> getAchievementActivities(User user) {
        List<Achievement> achievements = achievementRepository.findByUserIdOrderByEarnedAtDesc(user.getId());
        return achievements.stream()
                .filter(a -> a.getEarnedAt() != null)
                .map(achievement -> {
                    AchievementType type = achievement.getType();
                    return ActivityDto.builder()
                            .type(ActivityType.ACHIEVEMENT_EARNED.name())
                            .typeLabel(ActivityType.ACHIEVEMENT_EARNED.getLabel())
                            .emoji(ActivityType.ACHIEVEMENT_EARNED.getEmoji())
                            .title("Получил достижение: " + type.getName())
                            .description(type.getDescription())
                            .timestamp(achievement.getEarnedAt())
                            .category(type.getRarity())
                            .build();
                })
                .collect(Collectors.toList());
    }
}
