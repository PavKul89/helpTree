package org.example.helptreeservice.controller;

import org.example.helptreeservice.dto.posts.CreatePostRequest;
import org.example.helptreeservice.dto.posts.PostDto;
import org.example.helptreeservice.dto.posts.UpdatePostRequest;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.enums.TransactionType;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.PostService;
import org.example.helptreeservice.service.UserService;
import org.example.helptreeservice.service.WalletService;
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
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final AuthorizationService authService;
    private final UserService userService;
    private final WalletService walletService;
    
    private static final long BOOST_PRICE = 5L;

    @PostMapping
    public ResponseEntity<PostDto> createPost(@Valid @RequestBody CreatePostRequest request) {
        var user = authService.getCurrentUser();
        if (user == null) {
            throw new ForbiddenException("Для создания поста необходимо войти в систему");
        }
        if (userService.isUserBlocked(user.getUserId())) {
            throw new ForbiddenException("Ваш аккаунт заблокирован за долг. Помогите другим пользователям, чтобы разблокировать аккаунт.");
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
            @RequestParam(required = false) String city,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<PostDto> result = postService.getPosts(userId, status, title, authorName, category, city, pageable);
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

    @PostMapping("/by-ids")
    public ResponseEntity<List<PostDto>> getPostsByIds(@RequestBody List<Long> ids) {
        return ResponseEntity.ok(postService.getPostsByIds(ids));
    }

    @GetMapping("/map")
    public ResponseEntity<List<PostDto>> getPostsOnMap(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(defaultValue = "10") Double radius,
            @RequestParam(required = false) String status
    ) {
        return ResponseEntity.ok(postService.getPostsOnMap(latitude, longitude, radius, status));
    }

    @PostMapping("/{id}/boost")
    public ResponseEntity<Map<String, Object>> boostPost(@PathVariable Long id) {
        var currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            throw new ForbiddenException("Необходимо авторизоваться");
        }
        
        PostDto post = postService.getPostById(id);
        if (!authService.canManagePost(post.getUserId())) {
            throw new ForbiddenException("Вы можете поднять только свои посты");
        }
        
        if (post.getStatus() != PostStatus.OPEN) {
            throw new BadRequestException("Можно поднять только открытый пост");
        }
        
        try {
            walletService.spendCoins(currentUser.getUserId(), BOOST_PRICE, TransactionType.POST_BOOST, "Поднятие поста #" + id);
            PostDto boosted = postService.boostPost(id);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "post", boosted,
                "message", "Пост поднят в топ на 24 часа!"
            ));
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Не удалось поднять пост: " + e.getMessage());
        }
    }
}