package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.reviews.CreateReviewRequest;
import org.example.helptreeservice.dto.reviews.ReviewResponse;
import org.example.helptreeservice.dto.admin.AdminBulkResultDto;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.Review;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.AdminAuditAction;
import org.example.helptreeservice.enums.HelpStatus;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.UserBlockScope;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.mapper.ReviewMapper;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.ReviewRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private HelpRepository helpRepository;
    @Mock private UserRepository userRepository;
    @Mock private ReviewMapper reviewMapper;
    @Mock private UserService userService;
    @Mock private WalletService walletService;
    @Mock private RatingService ratingService;
    @Mock private UserBlockService userBlockService;
    @Mock private NotificationService notificationService;
    @Mock private AdminAuditService adminAuditService;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(
                reviewRepository, helpRepository, userRepository,
                reviewMapper, userService, walletService, ratingService,
                userBlockService, notificationService, adminAuditService);
    }

    @Test
    void createReview_shouldRejectBeforeConfirmation() {
        User helper = user(1L);
        User receiver = user(2L);
        Help help = help(10L, HelpStatus.COMPLETED, helper, receiver);
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(helpRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(help));

        assertThrows(BadRequestException.class,
                () -> reviewService.createReview(request(10L), 2L));
        verify(reviewRepository, never()).save(any(Review.class));
        verifyNoInteractions(walletService);
    }

    @Test
    void createReview_shouldRejectNonParticipant() {
        User helper = user(1L);
        User receiver = user(2L);
        User outsider = user(3L);
        Help help = help(10L, HelpStatus.CONFIRMED, helper, receiver);
        when(userRepository.findById(3L)).thenReturn(Optional.of(outsider));
        when(helpRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(help));

        assertThrows(ForbiddenException.class,
                () -> reviewService.createReview(request(10L), 3L));
        verify(reviewRepository, never()).save(any(Review.class));
        verifyNoInteractions(walletService);
    }

    @Test
    void createReview_shouldRejectWhenProfileBlocked() {
        User helper = user(1L);
        User receiver = user(2L);
        Help help = help(10L, HelpStatus.CONFIRMED, helper, receiver);
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(helpRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(help));
        doThrow(new ForbiddenException("blocked")).when(userBlockService)
                .assertInteractionAllowed(eq(2L), eq(1L), eq(UserBlockScope.PROFILE), anyString());

        assertThrows(ForbiddenException.class,
                () -> reviewService.createReview(request(10L), 2L));
        verify(reviewRepository, never()).save(any(Review.class));
        verifyNoInteractions(walletService);
    }

    @Test
    void createReview_shouldAwardBonusOnlyAfterConfirmationByParticipant() {
        User helper = user(1L);
        User receiver = user(2L);
        Help help = help(10L, HelpStatus.CONFIRMED, helper, receiver);
        when(userRepository.findById(2L)).thenReturn(Optional.of(receiver));
        when(helpRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(help));
        when(reviewRepository.existsByHelpAndFromUser(help, receiver)).thenReturn(false);
        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(invocation -> {
                    Review saved = invocation.getArgument(0);
                    saved.setId(77L);
                    return saved;
                });
        when(reviewMapper.toResponse(any(Review.class))).thenReturn(ReviewResponse.builder().build());

        reviewService.createReview(request(10L), 2L);

        verify(reviewRepository).save(any(Review.class));
        verify(walletService).addCoinsForReview(1L, 77L);
        verify(ratingService).recalculateUsers(List.of(1L));
    }

    @Test
    void deleteReview_shouldSoftDeleteAndRecalculateRecipientRating() {
        User helper = user(1L);
        User receiver = user(2L);
        Help help = help(10L, HelpStatus.CONFIRMED, helper, receiver);
        Review review = new Review();
        review.setId(77L);
        review.setHelp(help);
        review.setFromUser(receiver);
        review.setToUser(helper);
        review.setRating(5);
        review.setDeleted(false);
        when(reviewRepository.findById(77L)).thenReturn(Optional.of(review));

        reviewService.deleteReview(77L, 2L);

        assertEquals(true, review.getDeleted());
        verify(reviewRepository).save(review);
        verify(ratingService).recalculateUsers(List.of(1L));
    }

    @Test
    void getAdminReviews_shouldMapPageFromRepository() {
        User helper = user(1L);
        User receiver = user(2L);
        Help help = help(10L, HelpStatus.CONFIRMED, helper, receiver);
        Review review = new Review();
        review.setId(77L);
        review.setHelp(help);
        review.setFromUser(receiver);
        review.setToUser(helper);
        review.setRating(4);
        var pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        var page = new org.springframework.data.domain.PageImpl<>(List.of(review), pageable, 1);
        when(reviewRepository.findForAdmin(1L, 4, null, null, pageable)).thenReturn(page);
        when(reviewMapper.toResponse(review)).thenReturn(ReviewResponse.builder().id(77L).build());

        var result = reviewService.getAdminReviews(1L, 4, null, null, pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals(77L, result.getContent().get(0).getId());
    }

    @Test
    void adminDeleteReview_shouldRequireReason() {
        assertThrows(BadRequestException.class,
                () -> reviewService.adminDeleteReview(77L, 9L, "  "));

        verifyNoInteractions(adminAuditService);
        verify(reviewRepository, never()).save(any(Review.class));
    }

    @Test
    void adminDeleteReview_shouldSoftDeleteAndWriteAudit() {
        User admin = user(9L);
        admin.setRole(Role.ADMIN);
        User helper = user(1L);
        User receiver = user(2L);
        Review review = review(77L, help(10L, HelpStatus.CONFIRMED, helper, receiver), receiver, helper);
        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(reviewRepository.findById(77L)).thenReturn(Optional.of(review));

        reviewService.adminDeleteReview(77L, 9L, "  Оскорбление  ");

        assertEquals(true, review.getDeleted());
        assertNotNull(review.getDeletedAt());
        verify(reviewRepository).save(review);
        verify(ratingService).recalculateUsers(List.of(1L));
        verify(adminAuditService).record(eq(9L), eq(AdminAuditAction.REVIEW_DELETED),
                eq(AdminAuditService.ENTITY_REVIEW), eq(77L), anyMap(), anyMap(),
                eq("Оскорбление"));
    }

    @Test
    void bulkDeleteReviews_shouldReportPartialSuccess() {
        User admin = user(9L);
        admin.setRole(Role.ADMIN);
        User helper = user(1L);
        User receiver = user(2L);
        Review review = review(77L, help(10L, HelpStatus.CONFIRMED, helper, receiver), receiver, helper);
        when(userRepository.findById(9L)).thenReturn(Optional.of(admin));
        when(reviewRepository.findById(77L)).thenReturn(Optional.of(review));
        when(reviewRepository.findById(88L)).thenReturn(Optional.empty());

        AdminBulkResultDto result = reviewService.bulkDeleteReviews(
                List.of(77L, 88L), 9L, "Массовая очистка");

        assertEquals(2, result.getRequested());
        assertEquals(1, result.getSucceeded());
        assertEquals(1, result.getFailed());
        verify(adminAuditService).record(eq(9L), eq(AdminAuditAction.BULK_OPERATION),
                eq(AdminAuditService.ENTITY_REVIEW), isNull(), isNull(), anyMap(),
                eq("Массовая очистка"));
    }

    private CreateReviewRequest request(Long helpId) {
        CreateReviewRequest request = new CreateReviewRequest();
        request.setHelpId(helpId);
        request.setRating(5);
        return request;
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setName("User " + id);
        user.setDeleted(false);
        return user;
    }

    private Help help(Long id, HelpStatus status, User helper, User receiver) {
        Help help = new Help();
        help.setId(id);
        help.setHelper(helper);
        help.setReceiver(receiver);
        help.setStatus(status);
        help.setDeleted(false);
        return help;
    }

    private Review review(Long id, Help help, User from, User to) {
        Review review = new Review();
        review.setId(id);
        review.setHelp(help);
        review.setFromUser(from);
        review.setToUser(to);
        review.setRating(5);
        review.setDeleted(false);
        return review;
    }
}
