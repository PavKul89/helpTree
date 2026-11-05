package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.dto.activity.ActivityDto;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.enums.AchievementType;
import org.example.helptreeservice.enums.ActivityType;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.ActivityRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Locale;

/**
 * Activity feed assembled by one database query.
 *
 * <p>There is no in-memory merge and no per-source top-N load. Public viewers
 * only receive events that are safe to show on a public profile: confirmed
 * helps, public post statuses and achievements. Participant identifiers and
 * names, as well as debt blocks, are returned only to the owner or an admin.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private static final String ALL = "ALL";

    private final ActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;

    public PageResponse<ActivityDto> getUserActivities(Long userId,
                                                       String type,
                                                       int page,
                                                       int size) {
        if (!userRepository.existsByIdAndDeletedFalse(userId)) {
            throw new NotFoundException("User not found");
        }

        String eventType = resolveType(type);
        // canManageUser is true for the owner and for admins. Everyone else gets
        // the public projection enforced by the SQL visibility predicates below.
        boolean includePrivate = authorizationService.canManageUser(userId);

        Page<Object[]> rows = activityRepository.findUserActivity(
                userId, includePrivate, eventType, PageRequest.of(page, size));

        return PageResponse.from(rows.map(row -> toDto(row, includePrivate)));
    }

    private String resolveType(String type) {
        if (type == null || type.isBlank() || ALL.equalsIgnoreCase(type.trim())) {
            return ALL;
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        try {
            ActivityType.valueOf(normalized);
            return normalized;
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Неизвестный тип активности: " + type);
        }
    }

    private ActivityDto toDto(Object[] row, boolean includePrivate) {
        ActivityType type = ActivityType.valueOf((String) row[0]);
        Long relatedUserId = includePrivate ? asLong(row[3]) : null;
        String relatedUserName = includePrivate ? (String) row[4] : null;
        Long relatedPostId = asLong(row[5]);
        String relatedPostTitle = (String) row[6];
        String category = (String) row[7];
        String status = (String) row[8];
        String detail = (String) row[9];

        ActivityDto.ActivityDtoBuilder builder = ActivityDto.builder()
                .type(type.name())
                .typeLabel(type.getLabel())
                .emoji(type.getEmoji())
                .timestamp(asDateTime(row[2]))
                .relatedUserId(relatedUserId)
                .relatedUserName(relatedUserName)
                .relatedPostId(relatedPostId)
                .relatedPostTitle(relatedPostTitle)
                .category(category)
                .status(status);

        switch (type) {
            case HELP_GIVEN -> builder
                    .title("Помог пользователю")
                    .description(detail);
            case HELP_RECEIVED -> builder
                    .title("Получил помощь")
                    .description(detail);
            case POST_CREATED -> builder
                    .title("Создал запрос о помощи")
                    .description(detail);
            case ACHIEVEMENT_EARNED -> {
                AchievementType achievement = detail != null
                        ? AchievementType.valueOf(detail)
                        : null;
                builder.title(achievement != null
                                ? "Получил достижение: " + achievement.getName()
                                : "Получил достижение")
                        .description(achievement != null ? achievement.getDescription() : null)
                        .category(achievement != null ? achievement.getRarity() : null);
            }
            case USER_BLOCKED -> builder
                    .title("Заблокирован за долг")
                    .description("Аккаунт заблокирован из-за большого количества долгов. "
                            + "Помогите другим пользователям, чтобы разблокировать аккаунт.");
        }

        return builder.build();
    }

    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private LocalDateTime asDateTime(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toLocalDateTime();
        }
        return null;
    }
}
