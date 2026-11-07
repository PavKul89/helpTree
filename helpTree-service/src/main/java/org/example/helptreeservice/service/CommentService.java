package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.CommentProperties;
import org.example.helptreeservice.dto.comments.CommentActionResponse;
import org.example.helptreeservice.dto.comments.CommentPageResponse;
import org.example.helptreeservice.dto.comments.CommentResponse;
import org.example.helptreeservice.dto.comments.CreateCommentRequest;
import org.example.helptreeservice.entity.Comment;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.CommentSort;
import org.example.helptreeservice.enums.ModerationStatus;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.exception.AccountBlockedException;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.CommentMapper;
import org.example.helptreeservice.repository.CommentRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private static final String DELETE_REASON_AUTHOR = "AUTHOR";
    private static final String DELETE_REASON_MODERATION = "MODERATION";

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentMapper commentMapper;
    private final CommentContentSanitizer contentSanitizer;
    private final CommentRateLimiter rateLimiter;
    private final CommentProperties commentProperties;

    @Transactional
    public CommentActionResponse createComment(Long postId, CreateCommentRequest request, Long userId) {
        Post post = requirePost(postId);
        User user = requireActiveUser(userId);
        rateLimiter.checkAllowed(userId);

        String content = validateContent(request.getContent());

        Comment parent = null;
        int depth = 0;
        if (request.getParentCommentId() != null) {
            Long parentId = request.getParentCommentId();
            if (parentId <= 0) {
                throw new BadRequestException("parentCommentId должен быть положительным");
            }
            parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> new BadRequestException("Родительский комментарий не найден"));

            if (parent.getPost() == null || !parent.getPost().getId().equals(postId)) {
                throw new BadRequestException("Родительский комментарий принадлежит другому посту");
            }
            if (Boolean.TRUE.equals(parent.getDeleted())) {
                throw new BadRequestException("Нельзя ответить на удалённый комментарий");
            }
            if (parent.getModerationStatus() != ModerationStatus.ACTIVE) {
                throw new BadRequestException("Нельзя ответить на скрытый комментарий");
            }
            int parentDepth = parent.getDepth() == null ? 0 : parent.getDepth();
            if (parentDepth >= commentProperties.getMaxDepth()) {
                throw new BadRequestException(
                        "Достигнута максимальная глубина обсуждения (" + commentProperties.getMaxDepth() + ")");
            }
            depth = parentDepth + 1;
        }

        Comment comment = Comment.builder()
                .content(content)
                .post(post)
                .user(user)
                .parentCommentId(parent == null ? null : parent.getId())
                .depth(depth)
                .deleted(false)
                .deletedByAuthor(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Comment saved = commentRepository.saveAndFlush(comment);
        postRepository.incrementCommentsCount(postId);

        long totalComments = commentRepository.countByPostIdAndDeletedFalse(postId);
        log.info("Создан комментарий ID={} для поста ID={} от пользователя ID={}, parentId={}, depth={}",
                saved.getId(), postId, userId, saved.getParentCommentId(), depth);

        return CommentActionResponse.builder()
                .comment(commentMapper.toResponse(saved))
                .totalComments(totalComments)
                .build();
    }

    @Transactional(readOnly = true)
    public CommentPageResponse getCommentsByPost(Long postId, int page, int size, CommentSort sort) {
        if (!postRepository.existsByIdAndDeletedFalse(postId)) {
            throw new NotFoundException("Пост не найден");
        }
        if (page < 0) {
            throw new BadRequestException("Параметр page не может быть отрицательным");
        }
        if (size < 1) {
            throw new BadRequestException("Параметр size должен быть положительным");
        }

        int resolvedSize = Math.min(size, commentProperties.getMaxPageSize());
        Sort.Direction direction = sort == CommentSort.NEWEST ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, resolvedSize,
                Sort.by(direction, "createdAt").and(Sort.by(direction, "id")));

        Page<Comment> roots = commentRepository.findRootComments(postId, pageable);
        long totalComments = commentRepository.countByPostIdAndDeletedFalse(postId);

        if (roots.isEmpty()) {
            return CommentPageResponse.builder()
                    .content(List.of())
                    .page(page)
                    .size(resolvedSize)
                    .totalElements(roots.getTotalElements())
                    .totalPages(roots.getTotalPages())
                    .hasMore(false)
                    .totalComments(totalComments)
                    .build();
        }

        List<Long> rootIds = roots.getContent().stream().map(Comment::getId).toList();
        List<Long> threadIds = commentRepository.findThreadCommentIds(postId, rootIds);
        List<Comment> threadComments = commentRepository.findAllByIdInWithUser(threadIds);

        Map<Long, List<Comment>> childrenByParent = new HashMap<>();
        for (Comment comment : threadComments) {
            if (comment.getParentCommentId() != null) {
                childrenByParent.computeIfAbsent(comment.getParentCommentId(), key -> new ArrayList<>())
                        .add(comment);
            }
        }
        Comparator<Comment> byCreatedAt = Comparator
                .comparing(Comment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Comment::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        childrenByParent.values().forEach(children -> children.sort(byCreatedAt));

        List<CommentResponse> content = new ArrayList<>(threadComments.size());
        for (Comment root : roots.getContent()) {
            appendThread(root, childrenByParent, content);
        }

        return CommentPageResponse.builder()
                .content(content)
                .page(page)
                .size(resolvedSize)
                .totalElements(roots.getTotalElements())
                .totalPages(roots.getTotalPages())
                .hasMore(page + 1 < roots.getTotalPages())
                .totalComments(totalComments)
                .build();
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByUser(Long userId) {
        if (!userRepository.existsByIdAndDeletedFalse(userId)) {
            throw new NotFoundException("Пользователь не найден");
        }

        return commentMapper.toResponseList(commentRepository.findByUserIdWithUser(userId));
    }

    @Transactional
    public CommentActionResponse deleteComment(Long postId, Long commentId, Long userId) {
        requirePost(postId);
        Comment comment = requireCommentOfPost(postId, commentId);
        User actor = requireActiveUser(userId);

        boolean author = comment.getUser() != null && comment.getUser().getId().equals(userId);
        if (!author && actor.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Вы можете удалить только свой комментарий");
        }

        if (Boolean.TRUE.equals(comment.getDeleted())) {
            return actionResponse(comment, postId);
        }

        markDeleted(comment, actor, author,
                author ? DELETE_REASON_AUTHOR : DELETE_REASON_MODERATION);
        commentRepository.save(comment);
        postRepository.decrementCommentsCount(postId);
        commentRepository.flush();

        log.info("Комментарий ID={} поста ID={} помечен удалённым пользователем ID={} (author={})",
                commentId, postId, userId, author);
        return actionResponse(comment, postId);
    }

    /**
     * Soft-deletes a comment on behalf of moderation. Used by the report
     * resolution flow, which must not physically delete a parent and thereby
     * destroy the replies below it.
     */
    @Transactional
    public CommentActionResponse moderateDeleteComment(Long commentId, Long moderatorId, String reason) {
        User moderator = requireActiveUser(moderatorId);
        if (moderator.getRole() != Role.ADMIN) {
            throw new ForbiddenException("Только администратор может удалять комментарии по жалобе");
        }

        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Комментарий не найден"));
        if (Boolean.TRUE.equals(comment.getDeleted())) {
            return actionResponse(comment, comment.getPost().getId());
        }

        markDeleted(comment, moderator, false,
                reason == null || reason.isBlank() ? DELETE_REASON_MODERATION : truncate(reason, 40));
        commentRepository.save(comment);
        postRepository.decrementCommentsCount(comment.getPost().getId());
        commentRepository.flush();

        log.info("Комментарий ID={} поста ID={} удалён модератором ID={}",
                commentId, comment.getPost().getId(), moderatorId);
        return actionResponse(comment, comment.getPost().getId());
    }

    private void appendThread(Comment comment,
                              Map<Long, List<Comment>> childrenByParent,
                              List<CommentResponse> content) {
        content.add(commentMapper.toResponse(comment));
        for (Comment child : childrenByParent.getOrDefault(comment.getId(), List.of())) {
            appendThread(child, childrenByParent, content);
        }
    }

    private CommentActionResponse actionResponse(Comment comment, Long postId) {
        long totalComments = commentRepository.countByPostIdAndDeletedFalse(postId);
        return CommentActionResponse.builder()
                .comment(commentMapper.toResponse(comment))
                .totalComments(totalComments)
                .build();
    }

    private void markDeleted(Comment comment, User actor, boolean byAuthor, String reason) {
        LocalDateTime now = LocalDateTime.now();
        comment.setDeleted(true);
        comment.setDeletedAt(now);
        comment.setDeletedBy(actor);
        comment.setDeletedByAuthor(byAuthor);
        comment.setDeletedReason(truncate(reason, 40));
        comment.setUpdatedAt(now);
    }

    private Post requirePost(Long postId) {
        return postRepository.findByIdAndDeletedFalse(postId)
                .orElseThrow(() -> new NotFoundException("Пост не найден"));
    }

    private Comment requireCommentOfPost(Long postId, Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Комментарий не найден"));
        if (comment.getPost() == null || !comment.getPost().getId().equals(postId)) {
            throw new NotFoundException("Комментарий не найден");
        }
        return comment;
    }

    private User requireActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new NotFoundException("Пользователь не найден");
        }
        if (user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(LocalDateTime.now())) {
            throw new AccountBlockedException("Аккаунт заблокирован до " + user.getBlockedUntil());
        }
        return user;
    }

    private String validateContent(String rawContent) {
        String content = contentSanitizer.sanitize(rawContent);
        if (contentSanitizer.isBlank(content)) {
            throw new BadRequestException("Комментарий не может быть пустым");
        }
        if (contentSanitizer.exceedsMaxLength(content)) {
            throw new BadRequestException(
                    "Комментарий не может быть длиннее " + contentSanitizer.maxLength() + " символов");
        }
        return content;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
