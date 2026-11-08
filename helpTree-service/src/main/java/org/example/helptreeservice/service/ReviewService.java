package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.admin.AdminBulkResultDto;
import org.example.helptreeservice.dto.reviews.CreateReviewRequest;
import org.example.helptreeservice.dto.reviews.ReviewResponse;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.Review;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.AdminAuditAction;
import org.example.helptreeservice.enums.HelpStatus;
import org.example.helptreeservice.enums.NotificationType;
import org.example.helptreeservice.enums.UserBlockScope;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.ReviewMapper;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.ReviewRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.notification.NotificationCommand;
import org.example.helptreeservice.service.notification.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final HelpRepository helpRepository;
    private final UserRepository userRepository;
    private final ReviewMapper reviewMapper;
    private final UserService userService;
    private final WalletService walletService;
    private final RatingService ratingService;
    private final UserBlockService userBlockService;
    private final NotificationService notificationService;
    private final AdminAuditService adminAuditService;

    @Transactional
    public ReviewResponse createReview(CreateReviewRequest request, Long currentUserId) {
        log.info("Создание отзыва от пользователя {} для help {}", currentUserId, request.getHelpId());

        User fromUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        if (Boolean.TRUE.equals(fromUser.getDeleted())) {
            throw new NotFoundException("Пользователь не найден");
        }

        // Lock the help fact: status and participant checks stay stable while the
        // review (and its one-time bonus) is created.
        Help help = helpRepository.findByIdForUpdate(request.getHelpId())
                .orElseThrow(() -> new NotFoundException("Помощь не найдена"));
        if (Boolean.TRUE.equals(help.getDeleted())) {
            throw new NotFoundException("Помощь не найдена");
        }

        if (help.getStatus() != HelpStatus.CONFIRMED) {
            throw new BadRequestException("Отзыв можно оставить только после подтверждения помощи");
        }

        User toUser;
        if (help.getHelper().getId().equals(currentUserId)) {
            toUser = help.getReceiver();
        } else if (help.getReceiver().getId().equals(currentUserId)) {
            toUser = help.getHelper();
        } else {
            throw new ForbiddenException("Вы не являетесь участником этой помощи");
        }

        if (fromUser.getId().equals(toUser.getId())) {
            throw new BadRequestException("Нельзя оставить отзыв самому себе");
        }

        userBlockService.assertInteractionAllowed(fromUser.getId(), toUser.getId(), UserBlockScope.PROFILE,
                "Один из пользователей ограничил отзывы");

        // Учитываем и soft-deleted отзывы: иначе удаление позволяло бы
        // создать отзыв заново и повторно получить +2 HC.
        if (reviewRepository.existsByHelpAndFromUser(help, fromUser)) {
            throw new BadRequestException("Вы уже оставляли отзыв на эту помощь");
        }

        Review review = new Review();
        review.setHelp(help);
        review.setFromUser(fromUser);
        review.setToUser(toUser);
        review.setRating(request.getRating());
        review.setComment(request.getComment());
        review.setCreatedAt(LocalDateTime.now());

        Review savedReview = reviewRepository.save(review);
        log.info("Отзыв {} успешно создан", savedReview.getId());

        walletService.addCoinsForReview(toUser.getId(), savedReview.getId());
        log.info("Начислено 2 HC пользователю {} за отзыв {}", toUser.getId(), savedReview.getId());

        // The review is part of the rating only after it is actually stored.
        reviewRepository.flush();
        ratingService.recalculateUsers(List.of(toUser.getId()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reviewId", savedReview.getId());
        payload.put("helpId", help.getId());
        payload.put("rating", savedReview.getRating());
        payload.put("fromUserId", fromUser.getId());
        payload.put("fromUserName", fromUser.getName());
        notificationService.notify(NotificationCommand.of(
                toUser.getId(),
                NotificationType.NEW_REVIEW,
                "Новый отзыв",
                fromUser.getName() + " оставил отзыв " + savedReview.getRating()
                        + "/5 по помощи #" + help.getId() + ".",
                payload,
                "review:" + savedReview.getId()));

        return reviewMapper.toResponse(savedReview);
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsByHelp(Long helpId, Pageable pageable) {
        Help help = helpRepository.findById(helpId)
                .orElseThrow(() -> new NotFoundException("Помощь не найдена"));

        return reviewRepository.findByHelpWithDetails(help, pageable)
                .map(reviewMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getReviewsByUser(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        return reviewRepository.findByToUserWithFromUser(user, pageable)
                .map(reviewMapper::toResponse);
    }

    @Transactional
    public void deleteReview(Long reviewId, Long currentUserId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Отзыв не найден"));

        if (Boolean.TRUE.equals(review.getDeleted())) {
            throw new NotFoundException("Отзыв не найден");
        }

        if (!review.getFromUser().getId().equals(currentUserId)) {
            User currentUser = userRepository.findById(currentUserId)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
            if (currentUser.getRole() != org.example.helptreeservice.enums.Role.ADMIN) {
                throw new ForbiddenException("Вы можете удалить только свои отзывы");
            }
        }

        // Не удаляем строку физически: уникальный индекс (help_id, from_user_id)
        // должен продолжать блокировать повторное создание отзыва и повторный +2 HC.
        review.setDeleted(true);
        review.setDeletedAt(LocalDateTime.now());
        reviewRepository.save(review);
        log.info("Отзыв {} помечен как удалённый", reviewId);

        // Soft-delete is a moderation action: the recipient's rating must stop
        // counting the review immediately.
        reviewRepository.flush();
        ratingService.recalculateUsers(List.of(review.getToUser().getId()));
    }

    /**
     * Administrative review removal. Requires a reason, verifies the actor is a
     * real administrator and appends the removed review's state to the audit
     * trail. The row is soft-deleted so the one-review-per-help invariant keeps
     * holding.
     */
    @Transactional
    public void adminDeleteReview(Long reviewId, Long adminId, String reason) {
        String auditedReason = requireReason(reason);
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        if (admin.getRole() != org.example.helptreeservice.enums.Role.ADMIN) {
            throw new ForbiddenException("Только администратор может удалять любые отзывы");
        }

        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new NotFoundException("Отзыв не найден"));
        if (Boolean.TRUE.equals(review.getDeleted())) {
            throw new NotFoundException("Отзыв не найден");
        }

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("id", review.getId());
        before.put("rating", review.getRating());
        before.put("comment", review.getComment());
        before.put("fromUserId", review.getFromUser() == null ? null : review.getFromUser().getId());
        before.put("toUserId", review.getToUser() == null ? null : review.getToUser().getId());
        before.put("deleted", review.getDeleted());

        review.setDeleted(true);
        review.setDeletedAt(LocalDateTime.now());
        reviewRepository.save(review);
        reviewRepository.flush();
        ratingService.recalculateUsers(List.of(review.getToUser().getId()));
        log.info("Администратор {} удалил отзыв {}", adminId, reviewId);

        adminAuditService.record(adminId, AdminAuditAction.REVIEW_DELETED,
                AdminAuditService.ENTITY_REVIEW, reviewId,
                before, Map.of("deleted", true), auditedReason);
    }

    @Transactional
    public AdminBulkResultDto bulkDeleteReviews(List<Long> ids, Long adminId, String reason) {
        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("Список отзывов не может быть пустым");
        }
        String auditedReason = requireReason(reason);

        List<AdminBulkResultDto.Item> items = new java.util.ArrayList<>();
        int succeeded = 0;
        for (Long id : ids) {
            try {
                adminDeleteReview(id, adminId, auditedReason);
                succeeded++;
                items.add(AdminBulkResultDto.Item.builder().id(id).success(true).build());
            } catch (RuntimeException ex) {
                items.add(AdminBulkResultDto.Item.builder()
                        .id(id).success(false).error(ex.getMessage()).build());
            }
        }

        adminAuditService.record(adminId, AdminAuditAction.BULK_OPERATION,
                AdminAuditService.ENTITY_REVIEW, null, null,
                Map.of("operation", "DELETE", "requested", ids.size(), "succeeded", succeeded),
                auditedReason);

        return AdminBulkResultDto.builder()
                .action("DELETE")
                .requested(ids.size())
                .succeeded(succeeded)
                .failed(ids.size() - succeeded)
                .items(items)
                .build();
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Причина административного действия обязательна");
        }
        return reason.trim();
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getAdminReviews(Long userId,
                                                Integer rating,
                                                LocalDateTime from,
                                                LocalDateTime to,
                                                Pageable pageable) {
        return reviewRepository.findForAdmin(userId, rating, from, to, pageable)
                .map(reviewMapper::toResponse);
    }
}
