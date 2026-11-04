package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.reviews.CreateReviewRequest;
import org.example.helptreeservice.dto.reviews.ReviewResponse;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.Review;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.HelpStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.ReviewMapper;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.ReviewRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

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

        return reviewMapper.toResponse(savedReview);
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviewsByHelp(Long helpId) {
        Help help = helpRepository.findById(helpId)
                .orElseThrow(() -> new NotFoundException("Помощь не найдена"));

        return reviewRepository.findByHelpWithDetails(help).stream()
                .map(reviewMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getReviewsByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        return reviewRepository.findByToUserWithFromUser(user).stream()
                .map(reviewMapper::toResponse)
                .collect(Collectors.toList());
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
