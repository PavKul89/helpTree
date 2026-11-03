package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.reviews.CreateReviewRequest;
import org.example.helptreeservice.dto.reviews.ReviewResponse;
import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.Review;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.HelpStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.mapper.ReviewMapper;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.ReviewRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private HelpRepository helpRepository;
    @Mock private UserRepository userRepository;
    @Mock private ReviewMapper reviewMapper;
    @Mock private UserService userService;
    @Mock private WalletService walletService;

    private ReviewService reviewService;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(
                reviewRepository, helpRepository, userRepository,
                reviewMapper, userService, walletService);
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
}
