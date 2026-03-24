package org.example.helptreeservice.controller;

import org.example.helptreeservice.dto.posts.CreatePostRequest;
import org.example.helptreeservice.dto.posts.PostDto;
import org.example.helptreeservice.dto.posts.UpdatePostRequest;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final AuthorizationService authService;

    @PostMapping
    public ResponseEntity<PostDto> createPost(@Valid @RequestBody CreatePostRequest request) {
        var user = authService.getCurrentUser();
        if (user == null) {
            throw new ForbiddenException("Для создания поста необходимо войти в систему");
        }
        PostDto created = postService.createPost(request, user.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<Page<PostDto>> getPosts(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) PostStatus status,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String authorName,
            @RequestParam(required = false) String category,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<PostDto> result = postService.getPosts(userId, status, title, authorName, category, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PostDto> getPostById(@PathVariable Long id) {
        return ResponseEntity.ok(postService.getPostById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostDto> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePostRequest request) {
        PostDto post = postService.getPostById(id);
        if (!authService.canManagePost(post.getUserId())) {
            throw new ForbiddenException("Вы можете редактировать только свои посты");
        }
        return ResponseEntity.ok(postService.updatePost(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        PostDto post = postService.getPostById(id);
        if (!authService.canManagePost(post.getUserId())) {
            throw new ForbiddenException("Вы можете удалить только свои посты");
        }
        postService.deletePost(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<Void> restorePost(@PathVariable Long id) {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Только администратор может восстанавливать посты");
        }
        postService.restorePost(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PostDto>> getPostsByUser(@PathVariable Long userId) {
        if (!authService.canManagePost(userId)) {
            throw new ForbiddenException("Вы можете просматривать только свои посты");
        }
        return ResponseEntity.ok(postService.getPostsByUser(userId));
    }
}