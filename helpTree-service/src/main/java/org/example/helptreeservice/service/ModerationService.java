package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.FeatureFlags;
import org.example.helptreeservice.dto.reports.ReportTargetPreview;
import org.example.helptreeservice.entity.Comment;
import org.example.helptreeservice.entity.Message;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.Review;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.entity.UserBlock;
import org.example.helptreeservice.enums.FeatureFlag;
import org.example.helptreeservice.enums.ModerationActionType;
import org.example.helptreeservice.enums.ModerationStatus;
import org.example.helptreeservice.enums.ReportTargetType;
import org.example.helptreeservice.enums.UserBlockSource;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.CommentRepository;
import org.example.helptreeservice.repository.MessageRepository;
import org.example.helptreeservice.repository.ModerationActionRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.ReviewRepository;
import org.example.helptreeservice.repository.UserBlockRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The only place that knows how a moderation decision changes the domain.
 *
 * <p>It deliberately does not touch reports: the report lifecycle is owned by
 * {@link ReportService}. This service answers three questions:</p>
 * <ul>
 *   <li>is the reported object visible and may this user report it?</li>
 *   <li>what does an administrator see when opening the report?</li>
 *   <li>which content/account state change does an action produce?</li>
 * </ul>
 *
 * <p>Content visibility lives in {@code moderation_status} and remains
 * independent from the author's {@code deleted} flag. Account sanctions live in
 * {@code user_blocks} with {@code source = MODERATION}; the debt block fields on
 * {@code users} are never touched here.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModerationService {

    public static final int MAX_DESCRIPTION_LENGTH = 2000;

    private static final List<ReportTargetType> REPORTABLE_TARGETS = List.of(
            ReportTargetType.POST,
            ReportTargetType.USER,
            ReportTargetType.COMMENT,
            ReportTargetType.REVIEW,
            ReportTargetType.MESSAGE);

    private static final List<ModerationActionType> CONTENT_ACTIONS = List.of(
            ModerationActionType.HIDE_CONTENT,
            ModerationActionType.REMOVE_CONTENT,
            ModerationActionType.DELETE_CONTENT,
            ModerationActionType.RESTORE_CONTENT);

    private static final List<ModerationActionType> ACCOUNT_ACTIONS = List.of(
            ModerationActionType.WARN,
            ModerationActionType.MUTE,
            ModerationActionType.UNMUTE,
            ModerationActionType.BAN,
            ModerationActionType.UNBAN);

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ReviewRepository reviewRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;
    private final ModerationActionRepository moderationActionRepository;
    private final RatingService ratingService;
    private final FeatureFlags featureFlags;

    /** Owner of the reported object, resolved from the object itself. */
    public record TargetOwner(Long userId, User user) {
    }

    /**
     * Validates that a report can be created against this object.
     *
     * <p>Hidden/removed/deleted content is reported as "not found" so a closed
     * object is never revealed to a normal user. The only exception is a
     * {@code USER} target, where the account is still visible in the profile.</p>
     */
    @Transactional(readOnly = true)
    public TargetOwner requireReportable(ReportTargetType targetType, Long targetId, Long reporterId) {
        if (targetType == null || !REPORTABLE_TARGETS.contains(targetType)) {
            throw new BadRequestException("Жалобы на этот тип объекта не поддерживаются");
        }
        if (targetId == null || targetId <= 0) {
            throw new BadRequestException("ID объекта должен быть положительным");
        }

        TargetOwner owner = switch (targetType) {
            case POST -> ownerOfPost(postRepository.findById(targetId)
                    .orElseThrow(() -> new NotFoundException("Пост не найден")));
            case COMMENT -> ownerOfComment(commentRepository.findById(targetId)
                    .orElseThrow(() -> new NotFoundException("Комментарий не найден")));
            case REVIEW -> ownerOfReview(reviewRepository.findById(targetId)
                    .orElseThrow(() -> new NotFoundException("Отзыв не найден")));
            case USER -> new TargetOwner(targetId, userRepository.findById(targetId)
                    .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                    .filter(user -> user.getAnonymizedAt() == null)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден")));
            case MESSAGE -> ownerOfMessage(messageRepository.findById(targetId)
                    .orElseThrow(() -> new NotFoundException("Сообщение не найдено")), reporterId);
            default -> throw new BadRequestException("Жалобы на этот тип объекта не поддерживаются");
        };

        if (owner.userId() != null && owner.userId().equals(reporterId)) {
            throw new BadRequestException("Нельзя пожаловаться на собственный объект");
        }
        return owner;
    }

    private TargetOwner ownerOfPost(Post post) {
        if (Boolean.TRUE.equals(post.getDeleted())
                || post.getModerationStatus() != ModerationStatus.ACTIVE) {
            throw new NotFoundException("Пост не найден");
        }
        return new TargetOwner(
                post.getUser() == null ? null : post.getUser().getId(),
                post.getUser());
    }

    private TargetOwner ownerOfComment(Comment comment) {
        Post post = comment.getPost();
        if (post == null
                || Boolean.TRUE.equals(post.getDeleted())
                || post.getModerationStatus() != ModerationStatus.ACTIVE
                || Boolean.TRUE.equals(comment.getDeleted())
                || comment.getModerationStatus() != ModerationStatus.ACTIVE) {
            throw new NotFoundException("Комментарий не найден");
        }
        return new TargetOwner(comment.getUser().getId(), comment.getUser());
    }

    private TargetOwner ownerOfReview(Review review) {
        if (Boolean.TRUE.equals(review.getDeleted())
                || review.getModerationStatus() != ModerationStatus.ACTIVE) {
            throw new NotFoundException("Отзыв не найден");
        }
        // A review is authored by fromUser, so that is the account behind the
        // content. The recipient may report a review about themselves.
        return new TargetOwner(review.getFromUser().getId(), review.getFromUser());
    }

    private TargetOwner ownerOfMessage(Message message, Long reporterId) {
        if (message.getDeletedAt() != null
                || message.getChat() == null
                || !message.getChat().hasParticipant(reporterId)) {
            // A user outside the chat must not learn that the message exists.
            throw new NotFoundException("Сообщение не найдено");
        }
        return new TargetOwner(message.getSender().getId(), message.getSender());
    }

    /**
     * Administrative snapshot of the reported object. Unlike
     * {@link #requireReportable} it intentionally includes hidden and deleted
     * content, because moderation must be able to inspect what was reported.
     */
    @Transactional(readOnly = true)
    public ReportTargetPreview preview(ReportTargetType targetType, Long targetId) {
        if (targetType == null || targetId == null) {
            return ReportTargetPreview.builder().targetType(targetType).targetId(targetId)
                    .exists(false).build();
        }
        return switch (targetType) {
            case POST -> postRepository.findById(targetId)
                    .map(post -> previewOfPost(post))
                    .orElseGet(() -> missing(targetType, targetId));
            case COMMENT -> commentRepository.findById(targetId)
                    .map(comment -> previewOfComment(comment))
                    .orElseGet(() -> missing(targetType, targetId));
            case REVIEW -> reviewRepository.findById(targetId)
                    .map(review -> previewOfReview(review))
                    .orElseGet(() -> missing(targetType, targetId));
            case USER -> userRepository.findById(targetId)
                    .map(user -> previewOfUser(user))
                    .orElseGet(() -> missing(targetType, targetId));
            case MESSAGE -> messageRepository.findById(targetId)
                    .map(message -> previewOfMessage(message))
                    .orElseGet(() -> missing(targetType, targetId));
            default -> missing(targetType, targetId);
        };
    }

    private ReportTargetPreview previewOfPost(Post post) {
        return ReportTargetPreview.builder()
                .targetType(ReportTargetType.POST)
                .targetId(post.getId())
                .exists(true)
                .ownerId(post.getUser() == null ? null : post.getUser().getId())
                .ownerName(post.getUser() == null ? null : post.getUser().getName())
                .title(post.getTitle())
                .content(post.getDescription())
                .moderationStatus(post.getModerationStatus().name())
                .deleted(Boolean.TRUE.equals(post.getDeleted()))
                .createdAt(post.getCreatedAt())
                .build();
    }

    private ReportTargetPreview previewOfComment(Comment comment) {
        return ReportTargetPreview.builder()
                .targetType(ReportTargetType.COMMENT)
                .targetId(comment.getId())
                .exists(true)
                .ownerId(comment.getUser() == null ? null : comment.getUser().getId())
                .ownerName(comment.getUser() == null ? null : comment.getUser().getName())
                .title("Комментарий #" + comment.getId())
                .content(comment.getContent())
                .moderationStatus(comment.getModerationStatus().name())
                .deleted(Boolean.TRUE.equals(comment.getDeleted()))
                .createdAt(comment.getCreatedAt())
                .build();
    }

    private ReportTargetPreview previewOfReview(Review review) {
        return ReportTargetPreview.builder()
                .targetType(ReportTargetType.REVIEW)
                .targetId(review.getId())
                .exists(true)
                .ownerId(review.getFromUser() == null ? null : review.getFromUser().getId())
                .ownerName(review.getFromUser() == null ? null : review.getFromUser().getName())
                .title("Отзыв #" + review.getId() + " · " + review.getRating() + "/5")
                .content(review.getComment())
                .moderationStatus(review.getModerationStatus().name())
                .deleted(Boolean.TRUE.equals(review.getDeleted()))
                .createdAt(review.getCreatedAt())
                .build();
    }

    private ReportTargetPreview previewOfUser(User user) {
        return ReportTargetPreview.builder()
                .targetType(ReportTargetType.USER)
                .targetId(user.getId())
                .exists(true)
                .ownerId(user.getId())
                .ownerName(user.getName())
                .title(user.getName())
                .content(user.getEmail())
                .moderationStatus(null)
                .deleted(Boolean.TRUE.equals(user.getDeleted()))
                .createdAt(user.getCreatedAt())
                .build();
    }

    private ReportTargetPreview previewOfMessage(Message message) {
        return ReportTargetPreview.builder()
                .targetType(ReportTargetType.MESSAGE)
                .targetId(message.getId())
                .exists(true)
                .ownerId(message.getSender() == null ? null : message.getSender().getId())
                .ownerName(message.getSender() == null ? null : message.getSender().getName())
                .title("Сообщение #" + message.getId())
                .content(message.getContent())
                .moderationStatus(null)
                .deleted(message.getDeletedAt() != null)
                .createdAt(message.getCreatedAt())
                .build();
    }

    private ReportTargetPreview missing(ReportTargetType targetType, Long targetId) {
        return ReportTargetPreview.builder()
                .targetType(targetType)
                .targetId(targetId)
                .exists(false)
                .build();
    }

    /**
     * Applies a content visibility change. Returns an audit-friendly metadata
     * map that is stored both on the action and on the report event.
     */
    @Transactional
    public Map<String, Object> applyContentAction(ModerationActionType actionType,
                                                  ReportTargetType targetType,
                                                  Long targetId,
                                                  LocalDateTime expiresAt) {
        if (!CONTENT_ACTIONS.contains(actionType)) {
            throw new BadRequestException("Действие не изменяет видимость контента");
        }
        if (targetType == null || targetId == null) {
            throw new BadRequestException("Не указан объект модерации");
        }
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Время окончания действия должно быть в будущем");
        }

        ModerationStatus newStatus = switch (actionType) {
            case HIDE_CONTENT -> ModerationStatus.HIDDEN;
            case REMOVE_CONTENT, DELETE_CONTENT -> ModerationStatus.REMOVED;
            case RESTORE_CONTENT -> ModerationStatus.ACTIVE;
            default -> throw new BadRequestException("Неподдерживаемое действие с контентом");
        };

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actionType", actionType.name());
        metadata.put("targetType", targetType.name());
        metadata.put("targetId", targetId);
        metadata.put("expiresAt", expiresAt == null ? null : expiresAt.toString());

        switch (targetType) {
            case POST -> {
                Post post = postRepository.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("Пост не найден"));
                metadata.put("previousModerationStatus", post.getModerationStatus().name());
                metadata.put("targetUserId", post.getUser() == null ? null : post.getUser().getId());
                post.setModerationStatus(newStatus);
                post.setUpdatedAt(LocalDateTime.now());
                postRepository.save(post);
            }
            case COMMENT -> {
                Comment comment = commentRepository.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("Комментарий не найден"));
                ModerationStatus previousStatus = comment.getModerationStatus();
                metadata.put("previousModerationStatus", previousStatus.name());
                metadata.put("targetUserId", comment.getUser() == null ? null : comment.getUser().getId());
                comment.setModerationStatus(newStatus);
                comment.setUpdatedAt(LocalDateTime.now());
                commentRepository.save(comment);
                updateCommentsCounter(comment, previousStatus, newStatus);
            }
            case REVIEW -> {
                Review review = reviewRepository.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("Отзыв не найден"));
                metadata.put("previousModerationStatus", review.getModerationStatus().name());
                metadata.put("targetUserId",
                        review.getFromUser() == null ? null : review.getFromUser().getId());
                review.setModerationStatus(newStatus);
                reviewRepository.save(review);
                // The rating projection reads review rows, so the status change
                // must be visible to the recalculation that runs below.
                reviewRepository.flush();
                ratingService.recalculateUsers(
                        List.of(review.getToUser().getId()), "Модерация отзыва");
            }
            default -> throw new BadRequestException(
                    "Управление видимостью поддерживается только для постов, комментариев и отзывов");
        }

        metadata.put("moderationStatus", newStatus.name());
        log.info("Модерация: {} {} ID={} -> {}", actionType, targetType, targetId, newStatus);
        return metadata;
    }

    /**
     * Applies an account-level action. {@code WARN} has no state effect but is
     * still audited by the caller; mute/ban are stored as moderation blocks.
     */
    @Transactional
    public Map<String, Object> applyAccountAction(ModerationActionType actionType,
                                                  ReportTargetType targetType,
                                                  Long targetId,
                                                  User moderator,
                                                  String reason,
                                                  LocalDateTime expiresAt) {
        if (!ACCOUNT_ACTIONS.contains(actionType)) {
            throw new BadRequestException("Действие не является санкцией над аккаунтом");
        }
        if (expiresAt != null && !expiresAt.isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Время окончания действия должно быть в будущем");
        }
        if ((actionType == ModerationActionType.MUTE || actionType == ModerationActionType.BAN)
                && expiresAt == null) {
            // A permanent ban/mute is legitimate; nothing to validate here.
            log.debug("Бессрочная санкция {} для {} ID={}", actionType, targetType, targetId);
        }

        User target = resolveTargetUser(targetType, targetId);
        if (moderator != null && target.getId().equals(moderator.getId())) {
            throw new BadRequestException("Нельзя применить санкцию к собственной учётной записи");
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("actionType", actionType.name());
        metadata.put("targetUserId", target.getId());

        switch (actionType) {
            case WARN -> log.info("Модератор {} предупредил пользователя {}", moderatorId(moderator), target.getId());
            case MUTE, BAN -> {
                UserBlock block = UserBlock.builder()
                        .user(target)
                        .blockedBy(moderator)
                        .source(UserBlockSource.MODERATION)
                        .reason(reason)
                        .startsAt(LocalDateTime.now())
                        .endsAt(expiresAt)
                        .build();
                UserBlock saved = userBlockRepository.save(block);
                metadata.put("userBlockId", saved.getId());
                metadata.put("endsAt", expiresAt == null ? null : expiresAt.toString());
            }
            case UNMUTE, UNBAN -> {
                List<UserBlock> active = userBlockRepository.findActive(
                        target.getId(), UserBlockSource.MODERATION, LocalDateTime.now());
                if (active.isEmpty()) {
                    throw new ConflictException("У пользователя нет активной модерационной блокировки");
                }
                LocalDateTime now = LocalDateTime.now();
                for (UserBlock block : active) {
                    block.setLiftedAt(now);
                    block.setLiftedBy(moderator);
                    block.setLiftReason(reason);
                }
                userBlockRepository.saveAll(active);
                metadata.put("liftedBlocks", active.size());
            }
            default -> throw new BadRequestException("Неподдерживаемое действие с аккаунтом");
        }

        log.info("Модерация: {} для пользователя {} (модератор {})",
                actionType, target.getId(), moderatorId(moderator));
        return metadata;
    }

    /**
     * Restores content whose temporary hide has expired. The latest visibility
     * action per target wins, so a later permanent removal is never undone by an
     * older expired hide.
     */
    @Transactional
    public int restoreExpiredHiddenContent() {
        // A disabled moderation feature is a deliberate freeze: expiry must not
        // silently re-publish content while operators are holding the area.
        if (featureFlags.isDisabled(FeatureFlag.MODERATION)) {
            return 0;
        }
        List<org.example.helptreeservice.entity.ModerationAction> expired =
                moderationActionRepository.findExpiredHideActions(
                        ModerationActionType.HIDE_CONTENT, LocalDateTime.now());
        int restored = 0;
        for (var action : expired) {
            try {
                if (restoreIfStillHidden(action.getTargetType(), action.getTargetId())) {
                    restored++;
                    log.info("Временное скрытие истекло: {} ID={} восстановлен",
                            action.getTargetType(), action.getTargetId());
                }
            } catch (RuntimeException ex) {
                // A deleted target must not stop the whole batch.
                log.warn("Не удалось восстановить {} ID={}: {}",
                        action.getTargetType(), action.getTargetId(), ex.getMessage());
            }
        }
        return restored;
    }

    private boolean restoreIfStillHidden(ReportTargetType targetType, Long targetId) {
        return switch (targetType) {
            case POST -> postRepository.findById(targetId)
                    .filter(post -> post.getModerationStatus() == ModerationStatus.HIDDEN)
                    .map(post -> {
                        post.setModerationStatus(ModerationStatus.ACTIVE);
                        post.setUpdatedAt(LocalDateTime.now());
                        postRepository.save(post);
                        return true;
                    })
                    .orElse(false);
            case COMMENT -> commentRepository.findById(targetId)
                    .filter(comment -> comment.getModerationStatus() == ModerationStatus.HIDDEN)
                    .map(comment -> {
                        ModerationStatus previous = comment.getModerationStatus();
                        comment.setModerationStatus(ModerationStatus.ACTIVE);
                        comment.setUpdatedAt(LocalDateTime.now());
                        commentRepository.save(comment);
                        updateCommentsCounter(comment, previous, ModerationStatus.ACTIVE);
                        return true;
                    })
                    .orElse(false);
            case REVIEW -> reviewRepository.findById(targetId)
                    .filter(review -> review.getModerationStatus() == ModerationStatus.HIDDEN)
                    .map(review -> {
                        review.setModerationStatus(ModerationStatus.ACTIVE);
                        reviewRepository.save(review);
                        reviewRepository.flush();
                        ratingService.recalculateUsers(
                                List.of(review.getToUser().getId()), "Истечение скрытия отзыва");
                        return true;
                    })
                    .orElse(false);
            default -> false;
        };
    }

    /**
     * Keeps the denormalized {@code posts.comments_count} aligned with the
     * public comment set. Only a transition between public and non-public
     * changes the counter; editing a hidden comment does not.
     */
    private void updateCommentsCounter(Comment comment,
                                       ModerationStatus previousStatus,
                                       ModerationStatus newStatus) {
        if (comment.getPost() == null || Boolean.TRUE.equals(comment.getDeleted())) {
            return;
        }
        boolean wasPublic = previousStatus == ModerationStatus.ACTIVE;
        boolean isPublic = newStatus == ModerationStatus.ACTIVE;
        if (wasPublic && !isPublic) {
            postRepository.decrementCommentsCount(comment.getPost().getId());
        } else if (!wasPublic && isPublic) {
            postRepository.incrementCommentsCount(comment.getPost().getId());
        }
    }

    private User resolveTargetUser(ReportTargetType targetType, Long targetId) {        if (targetType == null || targetId == null) {
            throw new BadRequestException("Не указан объект санкции");
        }
        return switch (targetType) {
            case USER -> userRepository.findById(targetId)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
            case POST -> {
                Post post = postRepository.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("Пост не найден"));
                if (post.getUser() == null) {
                    throw new BadRequestException("У поста нет автора");
                }
                yield post.getUser();
            }
            case COMMENT -> {
                Comment comment = commentRepository.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("Комментарий не найден"));
                yield comment.getUser();
            }
            case REVIEW -> {
                Review review = reviewRepository.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("Отзыв не найден"));
                yield review.getFromUser();
            }
            case MESSAGE -> {
                Message message = messageRepository.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("Сообщение не найдено"));
                yield message.getSender();
            }
            default -> throw new BadRequestException(
                    "Санкции над аккаунтом не поддерживаются для этого типа объекта");
        };
    }

    private String moderatorId(User moderator) {
        return moderator == null ? "system" : String.valueOf(moderator.getId());
    }
}
