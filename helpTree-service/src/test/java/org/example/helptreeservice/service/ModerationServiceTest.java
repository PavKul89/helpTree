package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.reports.ReportTargetPreview;
import org.example.helptreeservice.entity.Chat;
import org.example.helptreeservice.entity.Comment;
import org.example.helptreeservice.entity.Message;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.Review;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.entity.UserBlock;
import org.example.helptreeservice.enums.ModerationActionType;
import org.example.helptreeservice.enums.ModerationStatus;
import org.example.helptreeservice.enums.ReportTargetType;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.UserBlockSource;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.CommentRepository;
import org.example.helptreeservice.repository.MessageRepository;
import org.example.helptreeservice.repository.ModerationActionRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.ReviewRepository;
import org.example.helptreeservice.repository.UserBlockRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModerationServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private ModerationActionRepository moderationActionRepository;
    @Mock
    private RatingService ratingService;

    private ModerationService moderationService;
    private User author;
    private User reporter;

    @BeforeEach
    void setUp() {
        moderationService = new ModerationService(
                postRepository,
                commentRepository,
                reviewRepository,
                messageRepository,
                userRepository,
                userBlockRepository,
                moderationActionRepository,
                ratingService);

        author = user(2L, "Bob", Role.USER);
        reporter = user(1L, "Alice", Role.USER);
    }

    private User user(Long id, String name, Role role) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setDeleted(false);
        user.setRole(role);
        return user;
    }

    private Post post(Long id, User owner) {
        Post post = new Post();
        post.setId(id);
        post.setDeleted(false);
        post.setUser(owner);
        post.setModerationStatus(ModerationStatus.ACTIVE);
        return post;
    }

    @Test
    void requireReportable_shouldRejectOwnPost() {
        when(postRepository.findById(5L)).thenReturn(Optional.of(post(5L, reporter)));

        assertThrows(BadRequestException.class, () ->
                moderationService.requireReportable(ReportTargetType.POST, 5L, 1L));
    }

    @Test
    void requireReportable_shouldHideModeratedPost() {
        Post hidden = post(5L, author);
        hidden.setModerationStatus(ModerationStatus.HIDDEN);
        when(postRepository.findById(5L)).thenReturn(Optional.of(hidden));

        assertThrows(NotFoundException.class, () ->
                moderationService.requireReportable(ReportTargetType.POST, 5L, 1L));
    }

    @Test
    void requireReportable_shouldAllowReviewRecipientToReportIt() {
        Review review = new Review();
        review.setId(9L);
        review.setFromUser(author);
        review.setToUser(reporter);
        review.setDeleted(false);
        review.setModerationStatus(ModerationStatus.ACTIVE);
        when(reviewRepository.findById(9L)).thenReturn(Optional.of(review));

        var owner = moderationService.requireReportable(ReportTargetType.REVIEW, 9L, 1L);

        assertEquals(2L, owner.userId());
    }

    @Test
    void requireReportable_shouldRejectReviewAuthorReportingTheirOwnReview() {
        Review review = new Review();
        review.setId(9L);
        review.setFromUser(reporter);
        review.setToUser(author);
        review.setDeleted(false);
        review.setModerationStatus(ModerationStatus.ACTIVE);
        when(reviewRepository.findById(9L)).thenReturn(Optional.of(review));

        assertThrows(BadRequestException.class, () ->
                moderationService.requireReportable(ReportTargetType.REVIEW, 9L, 1L));
    }

    @Test
    void requireReportable_shouldRejectMessageOutsideChat() {
        Chat chat = Chat.builder()
                .id(1L)
                .user1(author)
                .user2(user(3L, "Carol", Role.USER))
                .build();
        Message message = Message.builder()
                .id(7L)
                .chat(chat)
                .sender(author)
                .content("hello")
                .build();
        when(messageRepository.findById(7L)).thenReturn(Optional.of(message));

        assertThrows(NotFoundException.class, () ->
                moderationService.requireReportable(ReportTargetType.MESSAGE, 7L, 1L));
    }

    @Test
    void applyContentAction_shouldHidePost() {
        Post post = post(5L, author);
        when(postRepository.findById(5L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> metadata = moderationService.applyContentAction(
                ModerationActionType.HIDE_CONTENT, ReportTargetType.POST, 5L, LocalDateTime.now().plusDays(1));

        assertEquals(ModerationStatus.HIDDEN, post.getModerationStatus());
        assertEquals("HIDDEN", metadata.get("moderationStatus"));
        assertEquals("ACTIVE", metadata.get("previousModerationStatus"));
    }

    @Test
    void applyContentAction_shouldRemoveComment() {
        Comment comment = Comment.builder()
                .id(9L)
                .post(post(5L, author))
                .user(author)
                .content("bad")
                .deleted(false)
                .build();
        when(commentRepository.findById(9L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        moderationService.applyContentAction(
                ModerationActionType.REMOVE_CONTENT, ReportTargetType.COMMENT, 9L, null);

        assertEquals(ModerationStatus.REMOVED, comment.getModerationStatus());
    }

    @Test
    void applyContentAction_shouldRecalculateRatingWhenReviewIsRemoved() {
        Review review = new Review();
        review.setId(9L);
        review.setFromUser(author);
        review.setToUser(reporter);
        review.setDeleted(false);
        review.setModerationStatus(ModerationStatus.ACTIVE);
        when(reviewRepository.findById(9L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        moderationService.applyContentAction(
                ModerationActionType.REMOVE_CONTENT, ReportTargetType.REVIEW, 9L, null);

        assertEquals(ModerationStatus.REMOVED, review.getModerationStatus());
        verify(ratingService).recalculateUsers(List.of(1L), "Модерация отзыва");
    }

    @Test
    void applyAccountAction_shouldStoreModerationBlock() {
        User moderator = user(3L, "Admin", Role.ADMIN);
        when(userRepository.findById(2L)).thenReturn(Optional.of(author));
        when(userBlockRepository.save(any(UserBlock.class))).thenAnswer(invocation -> {
            UserBlock block = invocation.getArgument(0);
            block.setId(11L);
            return block;
        });

        LocalDateTime expiresAt = LocalDateTime.now().plusDays(3);
        Map<String, Object> metadata = moderationService.applyAccountAction(
                ModerationActionType.BAN, ReportTargetType.USER, 2L, moderator, "Abuse", expiresAt);

        ArgumentCaptor<UserBlock> captor = ArgumentCaptor.forClass(UserBlock.class);
        verify(userBlockRepository).save(captor.capture());
        UserBlock block = captor.getValue();
        assertEquals(UserBlockSource.MODERATION, block.getSource());
        assertEquals(author.getId(), block.getUser().getId());
        assertEquals(expiresAt, block.getEndsAt());
        assertEquals(11L, metadata.get("userBlockId"));
    }

    @Test
    void applyAccountAction_shouldRejectSanctionAgainstSelf() {
        User moderator = user(3L, "Admin", Role.ADMIN);
        when(userRepository.findById(3L)).thenReturn(Optional.of(moderator));

        assertThrows(BadRequestException.class, () -> moderationService.applyAccountAction(
                ModerationActionType.BAN, ReportTargetType.USER, 3L, moderator, "Self", null));
        verify(userBlockRepository, never()).save(any());
    }

    @Test
    void preview_shouldIncludeModeratedContentForAdministrators() {
        Post hidden = post(5L, author);
        hidden.setModerationStatus(ModerationStatus.REMOVED);
        hidden.setTitle("Reported post");
        hidden.setDescription("body");
        when(postRepository.findById(5L)).thenReturn(Optional.of(hidden));

        ReportTargetPreview preview = moderationService.preview(ReportTargetType.POST, 5L);

        assertTrue(preview.isExists());
        assertEquals("REMOVED", preview.getModerationStatus());
        assertEquals("Reported post", preview.getTitle());
    }
}
