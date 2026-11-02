package org.example.helptreeservice.service;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CommentMapper commentMapper;

    @InjectMocks
    private CommentService commentService;

    private Post post;
    private User author;

    @BeforeEach
    void setUp() {
        author = user(1L, "Alice", false);
        post = post(5L, author);
    }

    private User user(Long id, String name, boolean deleted) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setDeleted(deleted);
        return user;
    }

    private Post post(Long id, User owner) {
        Post post = new Post();
        post.setId(id);
        post.setTitle("Post");
        post.setUser(owner);
        post.setDeleted(false);
        return post;
    }

    @Test
    void createComment_shouldRejectDeletedAuthor() {
        User deleted = user(2L, "Deleted", true);
        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(post));
        when(userRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.empty());

        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("hello");

        assertThrows(NotFoundException.class, () -> commentService.createComment(5L, request, 2L));
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void createComment_shouldRejectParentFromAnotherPost() {
        User user = user(2L, "Bob", false);
        Comment parent = Comment.builder()
                .id(7L)
                .post(post(6L, user))
                .user(user)
                .content("parent")
                .build();

        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(post));
        when(userRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(user));
        when(commentRepository.findById(7L)).thenReturn(Optional.of(parent));

        CreateCommentRequest request = new CreateCommentRequest();
        request.setContent("reply");
        request.setParentCommentId(7L);

        assertThrows(BadRequestException.class, () -> commentService.createComment(5L, request, 2L));
        verify(commentRepository, never()).saveAndFlush(any());
    }

    @Test
    void deleteComment_shouldRejectCommentFromAnotherPost() {
        when(postRepository.existsByIdAndDeletedFalse(5L)).thenReturn(true);
        Comment comment = Comment.builder()
                .id(9L)
                .post(post(6L, author))
                .user(author)
                .content("foreign")
                .build();
        when(commentRepository.findById(9L)).thenReturn(Optional.of(comment));

        assertThrows(NotFoundException.class,
                () -> commentService.deleteComment(5L, 9L, 1L));
        verify(commentRepository, never()).delete(any());
    }

    @Test
    void deleteComment_shouldForbidDeletingSomeoneElsesComment() {
        when(postRepository.existsByIdAndDeletedFalse(5L)).thenReturn(true);
        Comment comment = Comment.builder()
                .id(9L)
                .post(post)
                .user(user(2L, "Bob", false))
                .content("Bob's")
                .build();
        when(commentRepository.findById(9L)).thenReturn(Optional.of(comment));

        assertThrows(ForbiddenException.class,
                () -> commentService.deleteComment(5L, 9L, 1L));
        verify(commentRepository, never()).delete(any());
    }

    @Test
    void deleteComment_shouldDeleteOwnCommentOfSamePost() {
        when(postRepository.existsByIdAndDeletedFalse(5L)).thenReturn(true);
        Comment comment = Comment.builder()
                .id(9L)
                .post(post)
                .user(author)
                .content("mine")
                .build();
        when(commentRepository.findById(9L)).thenReturn(Optional.of(comment));

        commentService.deleteComment(5L, 9L, 1L);

        verify(commentRepository).delete(comment);
    }

    @Test
    void getCommentsByPost_shouldRejectDeletedPost() {
        when(postRepository.existsByIdAndDeletedFalse(5L)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> commentService.getCommentsByPost(5L));
        verify(commentRepository, never()).findByPostIdOrderByCreatedAtAsc(anyLong());
    }
}
