package org.example.helptreeservice.service;

import org.example.helptreeservice.config.CommentProperties;
import org.example.helptreeservice.dto.comments.CommentActionResponse;
import org.example.helptreeservice.dto.comments.CommentPageResponse;
import org.example.helptreeservice.dto.comments.CommentResponse;
import org.example.helptreeservice.dto.comments.CreateCommentRequest;
import org.example.helptreeservice.entity.Comment;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.CommentSort;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.UserBlockScope;
import org.example.helptreeservice.exception.AccountBlockedException;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.CommentMapper;
import org.example.helptreeservice.repository.CommentRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.ratelimit.DistributedRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CommentMapper commentMapper;
    @Mock
    private UserBlockService userBlockService;

    private CommentProperties properties;
    private CommentService commentService;

    private Post post;
    private User author;

    @BeforeEach
    void setUp() {
        properties = new CommentProperties();
        properties.setRateLimitEnabled(false);
        commentService = new CommentService(
                commentRepository,
                postRepository,
                userRepository,
                commentMapper,
                new CommentContentSanitizer(properties),
                new CommentRateLimiter(properties, mock(DistributedRateLimiter.class)),
                properties,
                userBlockService);

        author = user(1L, "Alice", false, Role.USER);
        post = post(5L, author);

        when(commentMapper.toResponse(any(Comment.class))).thenAnswer(invocation -> {
            Comment comment = invocation.getArgument(0);
            boolean deleted = Boolean.TRUE.equals(comment.getDeleted());
            return CommentResponse.builder()
                    .id(comment.getId())
                    .postId(comment.getPost() == null ? null : comment.getPost().getId())
                    .userId(comment.getUser() == null ? null : comment.getUser().getId())
                    .userName(comment.getUser() == null ? null : comment.getUser().getName())
                    .content(deleted ? null : comment.getContent())
                    .parentCommentId(comment.getParentCommentId())
                    .depth(comment.getDepth())
                    .createdAt(comment.getCreatedAt())
                    .deleted(deleted)
                    .deletedByAuthor(Boolean.TRUE.equals(comment.getDeletedByAuthor()))
                    .deletedAt(comment.getDeletedAt())
                    .build();
        });
    }

    private User user(Long id, String name, boolean deleted, Role role) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setDeleted(deleted);
        user.setRole(role);
        return user;
    }

    private Post post(Long id, User owner) {
        Post post = new Post();
        post.setId(id);
        post.setTitle("Post");
        post.setUser(owner);
        post.setDeleted(false);
        post.setCommentsCount(0);
        return post;
    }

    private Comment comment(Long id, Post commentPost, User commentAuthor, Long parentId, int depth) {
        return Comment.builder()
                .id(id)
                .post(commentPost)
                .user(commentAuthor)
                .content("comment-" + id)
                .parentCommentId(parentId)
                .depth(depth)
                .deleted(false)
                .deletedByAuthor(false)
                .createdAt(LocalDateTime.now().plusSeconds(id))
                .build();
    }

    private CreateCommentRequest request(String content, Long parentId) {
        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent(content);
        request.setParentCommentId(parentId);
        return request;
    }

    @Test
    void createComment_shouldRejectDeletedAuthor() {
        User deleted = user(2L, "Deleted", true, Role.USER);
        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(post));
        when(userRepository.findById(2L)).thenReturn(Optional.of(deleted));

        assertThrows(NotFoundException.class,
                () -> commentService.createComment(5L, request("hello", null), 2L));
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createComment_shouldRejectBlockedAuthor() {
        User blocked = user(2L, "Blocked", false, Role.USER);
        blocked.setBlockedUntil(LocalDateTime.now().plusDays(1));
        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(post));
        when(userRepository.findById(2L)).thenReturn(Optional.of(blocked));

        assertThrows(AccountBlockedException.class,
                () -> commentService.createComment(5L, request("hello", null), 2L));
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createComment_shouldRejectDeletedPost() {
        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> commentService.createComment(5L, request("hello", null), 1L));
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createComment_shouldRejectWhenPostAuthorBlockedCommenter() {
        User bob = user(2L, "Bob", false, Role.USER);
        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(post));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        doThrow(new ForbiddenException("blocked")).when(userBlockService)
                .assertInteractionAllowed(eq(2L), eq(1L), eq(UserBlockScope.ALL), anyString());

        assertThrows(ForbiddenException.class,
                () -> commentService.createComment(5L, request("hello", null), 2L));
        verify(userBlockService).assertInteractionAllowed(2L, 1L, UserBlockScope.ALL,
                "Автор заявки ограничил комментарии");
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createComment_shouldRejectParentFromAnotherPost() {
        User bob = user(2L, "Bob", false, Role.USER);
        Comment parent = comment(7L, post(6L, bob), bob, null, 0);

        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(post));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(commentRepository.findById(7L)).thenReturn(Optional.of(parent));

        assertThrows(BadRequestException.class,
                () -> commentService.createComment(5L, request("reply", 7L), 2L));
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createComment_shouldRejectDeletedParent() {
        User bob = user(2L, "Bob", false, Role.USER);
        Comment parent = comment(7L, post, bob, null, 0);
        parent.setDeleted(true);
        parent.setDeletedAt(LocalDateTime.now());

        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(post));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(commentRepository.findById(7L)).thenReturn(Optional.of(parent));

        assertThrows(BadRequestException.class,
                () -> commentService.createComment(5L, request("reply", 7L), 2L));
    }

    @Test
    void createComment_shouldRejectReplyDeeperThanMaxDepth() {
        User bob = user(2L, "Bob", false, Role.USER);
        Comment parent = comment(7L, post, bob, 6L, properties.getMaxDepth());

        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(post));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(commentRepository.findById(7L)).thenReturn(Optional.of(parent));

        assertThrows(BadRequestException.class,
                () -> commentService.createComment(5L, request("deep", 7L), 2L));
    }

    @Test
    void createComment_shouldSanitizeAndReturnAuthorDateAndParent() {
        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(commentRepository.saveAndFlush(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(commentRepository.countByPostIdAndDeletedFalse(5L)).thenReturn(1L);

        CommentActionResponse result = commentService.createComment(
                5L, request("  hello\u0000 world  ", null), 1L);

        assertEquals("hello world", result.getComment().getContent());
        assertEquals(1L, result.getTotalComments());
        assertEquals(10L, result.getComment().getId());
        assertEquals(1L, result.getComment().getUserId());
        assertEquals("Alice", result.getComment().getUserName());
        assertNotNull(result.getComment().getCreatedAt());
        assertNull(result.getComment().getParentCommentId());
        assertEquals(0, result.getComment().getDepth());
        verify(postRepository).incrementCommentsCount(5L);
    }

    @Test
    void createComment_shouldStoreReplyDepthAndParent() {
        User bob = user(2L, "Bob", false, Role.USER);
        Comment parent = comment(7L, post, author, null, 1);

        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(post));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(commentRepository.findById(7L)).thenReturn(Optional.of(parent));
        when(commentRepository.saveAndFlush(any(Comment.class))).thenAnswer(invocation -> {
            Comment saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });
        when(commentRepository.countByPostIdAndDeletedFalse(5L)).thenReturn(2L);

        CommentActionResponse result = commentService.createComment(5L, request("reply", 7L), 2L);

        assertEquals(7L, result.getComment().getParentCommentId());
        assertEquals(2, result.getComment().getDepth());
        assertEquals(2L, result.getTotalComments());
    }

    @Test
    void createComment_shouldRejectBlankContentAfterSanitizing() {
        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));

        assertThrows(BadRequestException.class,
                () -> commentService.createComment(5L, request("\u0000 \n\t", null), 1L));
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createComment_shouldRejectTooLongContent() {
        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(post));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));

        String tooLong = "x".repeat(properties.getMaxLength() + 1);
        assertThrows(BadRequestException.class,
                () -> commentService.createComment(5L, request(tooLong, null), 1L));
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void getCommentsByPost_shouldRejectDeletedPost() {
        when(postRepository.existsByIdAndDeletedFalse(5L)).thenReturn(false);

        assertThrows(NotFoundException.class,
                () -> commentService.getCommentsByPost(5L, 0, 20, CommentSort.OLDEST));
        verify(commentRepository, never()).findRootComments(any(), any());
    }

    @Test
    void getCommentsByPost_shouldPageRootsAndIncludeWholeThreads() {
        User bob = user(2L, "Bob", false, Role.USER);
        Comment root = comment(10L, post, author, null, 0);
        Comment child = comment(11L, post, bob, 10L, 1);
        Comment grandchild = comment(12L, post, author, 11L, 2);
        Comment secondRoot = comment(13L, post, bob, null, 0);
        secondRoot.setDeleted(true);
        secondRoot.setDeletedAt(LocalDateTime.now());
        secondRoot.setDeletedByAuthor(true);

        Page<Comment> rootPage = new PageImpl<>(List.of(root, secondRoot),
                PageRequest.of(0, 2), 5);
        when(postRepository.existsByIdAndDeletedFalse(5L)).thenReturn(true);
        when(commentRepository.findRootComments(eq(5L), any(Pageable.class))).thenReturn(rootPage);
        when(commentRepository.findThreadCommentIds(eq(5L), any()))
                .thenReturn(List.of(10L, 11L, 12L, 13L));
        when(commentRepository.findAllByIdInWithUser(any()))
                .thenReturn(List.of(root, child, grandchild, secondRoot));
        when(commentRepository.countByPostIdAndDeletedFalse(5L)).thenReturn(3L);

        CommentPageResponse response = commentService.getCommentsByPost(5L, 0, 2, CommentSort.OLDEST);

        assertEquals(List.of(10L, 11L, 12L, 13L),
                response.getContent().stream().map(CommentResponse::getId).toList());
        assertEquals(5L, response.getTotalElements());
        assertEquals(3, response.getTotalPages());
        assertTrue(response.isHasMore());
        assertEquals(3L, response.getTotalComments());
        CommentResponse tombstone = response.getContent().get(3);
        assertTrue(tombstone.isDeleted());
        assertTrue(tombstone.isDeletedByAuthor());
        assertNull(tombstone.getContent());
    }

    @Test
    void deleteComment_shouldRejectCommentFromAnotherPost() {
        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(post));
        Comment comment = comment(9L, post(6L, author), author, null, 0);
        when(commentRepository.findById(9L)).thenReturn(Optional.of(comment));

        assertThrows(NotFoundException.class, () -> commentService.deleteComment(5L, 9L, 1L));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void deleteComment_shouldForbidDeletingSomeoneElsesComment() {
        User bob = user(2L, "Bob", false, Role.USER);
        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(post));
        Comment comment = comment(9L, post, bob, null, 0);
        when(commentRepository.findById(9L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));

        assertThrows(ForbiddenException.class, () -> commentService.deleteComment(5L, 9L, 1L));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void deleteComment_shouldCreateAuthorTombstoneAndKeepTree() {
        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(post));
        Comment comment = comment(9L, post, author, null, 0);
        when(commentRepository.findById(9L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(commentRepository.countByPostIdAndDeletedFalse(5L)).thenReturn(2L);

        CommentActionResponse result = commentService.deleteComment(5L, 9L, 1L);

        assertTrue(comment.getDeleted());
        assertTrue(comment.getDeletedByAuthor());
        assertNotNull(comment.getDeletedAt());
        assertEquals(author, comment.getDeletedBy());
        assertTrue(result.getComment().isDeleted());
        assertNull(result.getComment().getContent());
        assertEquals(2L, result.getTotalComments());
        verify(commentRepository).save(comment);
        verify(commentRepository, never()).delete(any());
        verify(postRepository).decrementCommentsCount(5L);
    }

    @Test
    void deleteComment_shouldAllowAdminToDeleteSomeoneElsesComment() {
        User admin = user(3L, "Admin", false, Role.ADMIN);
        User bob = user(2L, "Bob", false, Role.USER);
        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(post));
        Comment comment = comment(9L, post, bob, null, 0);
        when(commentRepository.findById(9L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
        when(commentRepository.countByPostIdAndDeletedFalse(5L)).thenReturn(0L);

        CommentActionResponse result = commentService.deleteComment(5L, 9L, 3L);

        assertTrue(comment.getDeleted());
        assertFalse(comment.getDeletedByAuthor());
        assertEquals(admin, comment.getDeletedBy());
        assertTrue(result.getComment().isDeleted());
        verify(postRepository).decrementCommentsCount(5L);
    }

    @Test
    void deleteComment_shouldBeIdempotentForAlreadyDeletedComment() {
        Comment comment = comment(9L, post, author, null, 0);
        comment.setDeleted(true);
        comment.setDeletedAt(LocalDateTime.now());
        comment.setDeletedByAuthor(true);
        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(post));
        when(commentRepository.findById(9L)).thenReturn(Optional.of(comment));
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(commentRepository.countByPostIdAndDeletedFalse(5L)).thenReturn(1L);

        CommentActionResponse result = commentService.deleteComment(5L, 9L, 1L);

        assertEquals(1L, result.getTotalComments());
        verify(commentRepository, never()).save(any());
        verify(postRepository, never()).decrementCommentsCount(any());
    }

    @Test
    void moderateDeleteComment_shouldMarkTombstoneAsModerationRemoval() {
        User admin = user(3L, "Admin", false, Role.ADMIN);
        User bob = user(2L, "Bob", false, Role.USER);
        Comment comment = comment(9L, post, bob, null, 0);
        when(userRepository.findById(3L)).thenReturn(Optional.of(admin));
        when(commentRepository.findById(9L)).thenReturn(Optional.of(comment));
        when(commentRepository.countByPostIdAndDeletedFalse(5L)).thenReturn(0L);

        CommentActionResponse result = commentService.moderateDeleteComment(9L, 3L, "abuse");

        assertTrue(comment.getDeleted());
        assertFalse(comment.getDeletedByAuthor());
        assertEquals("abuse", comment.getDeletedReason());
        assertNull(result.getComment().getContent());
        verify(postRepository).decrementCommentsCount(5L);
    }
}
