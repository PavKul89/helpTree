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

    @Transactional
    public ReviewResponse createReview(CreateReviewRequest request, Long currentUserId) {
        log.info("Создание отзыва от пользователя {} для help {}", currentUserId, request.getHelpId());

        User fromUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        Help help = helpRepository.findById(request.getHelpId())
                .orElseThrow(() -> new NotFoundException("Помощь не найдена"));

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

        walletService.addCoinsForReview(toUser.getId());
        log.info("Начислено 2 HC пользователю {} за отзыв", toUser.getId());

        updateUserRating(toUser);

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

        if (!review.getFromUser().getId().equals(currentUserId)) {
            User currentUser = userRepository.findById(currentUserId)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
            if (currentUser.getRole() != org.example.helptreeservice.enums.Role.ADMIN) {
                throw new ForbiddenException("Вы можете удалить только свои отзывы");
            }
        }

        User toUser = review.getToUser();
        reviewRepository.delete(review);
        log.info("Отзыв {} удалён", reviewId);
        
        updateUserRating(toUser);
    }
    
    private void updateUserRating(User user) {
        List<Review> reviews = reviewRepository.findByToUserWithFromUser(user);
        if (reviews.isEmpty()) {
            userService.updateUserRating(user.getId(), 0.0);
        } else {
            double avgRating = reviews.stream()
                    .mapToInt(r -> r.getRating() != null ? r.getRating() : 0)
                    .average()
                    .orElse(0.0);
            userService.updateUserRating(user.getId(), Math.round(avgRating * 10.0) / 10.0);
        }
    }
}
