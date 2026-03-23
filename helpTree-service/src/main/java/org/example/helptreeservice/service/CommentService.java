package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.comments.CommentResponse;
import org.example.helptreeservice.dto.comments.CreateCommentRequest;
import org.example.helptreeservice.entity.Comment;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.CommentMapper;
import org.example.helptreeservice.repository.CommentRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentMapper commentMapper;

    @Transactional
    public CommentResponse createComment(Long postId, CreateCommentRequest request, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Пост не найден"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        if (request.getParentCommentId() != null) {
            Comment parentComment = commentRepository.findById(request.getParentCommentId())
                    .orElseThrow(() -> new NotFoundException("Родительский комментарий не найден"));
            
            if (!parentComment.getPost().getId().equals(postId)) {
                throw new BadRequestException("Родительский комментарий принадлежит другому посту");
            }
        }

        Comment comment = Comment.builder()
                .content(request.getContent())
                .post(post)
                .user(user)
                .parentCommentId(request.getParentCommentId())
                .build();

        Comment saved = commentRepository.saveAndFlush(comment);
        log.info("Создан комментарий ID={} для поста ID={} от пользователя ID={}, parentId={}", 
                saved.getId(), postId, userId, request.getParentCommentId());

        return commentMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByPost(Long postId) {
        if (!postRepository.existsById(postId)) {
            throw new NotFoundException("Пост не найден");
        }

        List<Comment> comments = commentRepository.findByPostIdOrderByCreatedAtAsc(postId);
        return commentMapper.toResponseList(comments);
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getCommentsByUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new NotFoundException("Пользователь не найден");
        }

        List<Comment> comments = commentRepository.findByUserId(userId);
        return commentMapper.toResponseList(comments);
    }

    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new NotFoundException("Комментарий не найден"));

        if (!comment.getUser().getId().equals(userId)) {
            throw new ForbiddenException("Вы можете удалить только свой комментарий");
        }

        commentRepository.delete(comment);
        log.info("Удалён комментарий ID={} пользователем ID={}", commentId, userId);
    }
}
