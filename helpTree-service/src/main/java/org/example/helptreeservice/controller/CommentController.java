package org.example.helptreeservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.comments.CommentResponse;
import org.example.helptreeservice.dto.comments.CreateCommentRequest;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.CommentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts/{postId}/comments")
@RequiredArgsConstructor
@Slf4j
public class CommentController {

    private final CommentService commentService;
    private final AuthorizationService authorizationService;

    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CreateCommentRequest request,
            HttpServletRequest httpRequest) {

        AuthorizationService.UserContext user = authorizationService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }
        CommentResponse response = commentService.createComment(postId, request, user.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CommentResponse>> getComments(@PathVariable Long postId) {
        List<CommentResponse> comments = commentService.getCommentsByPost(postId);
        return ResponseEntity.ok(comments);
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            HttpServletRequest httpRequest) {

        AuthorizationService.UserContext user = authorizationService.getCurrentUser();
        if (user == null) {
            throw new UnauthorizedException("Требуется авторизация");
        }
        commentService.deleteComment(commentId, user.getUserId());
        return ResponseEntity.noContent().build();
    }
}
