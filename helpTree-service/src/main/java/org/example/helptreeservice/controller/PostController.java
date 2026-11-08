package org.example.helptreeservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.dto.posts.CreatePostRequest;
import org.example.helptreeservice.dto.posts.PostDto;
import org.example.helptreeservice.dto.posts.PublicPostDto;
import org.example.helptreeservice.dto.posts.UpdatePostRequest;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.PostService;
import org.example.helptreeservice.service.PrivacyPolicy;
import org.example.helptreeservice.service.UserService;
import org.example.helptreeservice.util.PageRequests;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Tag(name = "Posts", description = "Заявки о помощи")
public class PostController {

    private final PostService postService;
    private final AuthorizationService authService;
    private final UserService userService;
    private final PrivacyPolicy privacyPolicy;
    private final EconomyConfig economyConfig;
    private final PaginationConfig paginationConfig;

    @PostMapping
    @Operation(summary = "Создать заявку о помощи")
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
    @Operation(summary = "Лента заявок (постранично)",
            description = "Публичная лента. Отменённые заявки скрыты, если вызывающий не автор и не ADMIN. "
                    + "Ответ — PageResponse.")
    @SecurityRequirements
    public ResponseEntity<PageResponse<PublicPostDto>> getPosts(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) PostStatus status,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String authorName,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String city,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        // Публичная лента не показывает отменённые посты: для выборки по автору решают его права,
        // для общей ленты — только администратор видит все статусы.
        boolean excludeCancelled = userId != null
                ? !privacyPolicy.canViewAllPostStatuses(userId)
                : !privacyPolicy.canViewAllPostStatuses(null);
        Pageable safePageable = PageRequests.of(pageable.getPageNumber(), pageable.getPageSize(),
                paginationConfig, pageable.getSort());
        Page<PublicPostDto> result = postService.getPosts(
                userId, status, title, authorName, category, city, safePageable, excludeCancelled);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    /**
     * Public post detail. The full projection is returned only to the author
     * and admins; every other caller receives the public projection with
     * rounded coordinates and no boost expiry.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Заявка по id",
            description = "Публичная проекция для всех, кроме автора и ADMIN, которые получают полную проекцию.")
    @SecurityRequirements
    public ResponseEntity<?> getPostById(@PathVariable Long id) {
        PostDto post = postService.getPostById(id);
        if (privacyPolicy.canViewFullPost(post.getUserId())) {
            return ResponseEntity.ok(post);
        }
        return ResponseEntity.ok(postService.toPublicPost(post));
    }

    /** Authenticated owner view with every status and the full projection. */
    @GetMapping("/mine")
    @Operation(summary = "Мои заявки (постранично)",
            description = "Авторизованный владельческий вид: все статусы и полная проекция.")
    public ResponseEntity<PageResponse<PostDto>> getMyPosts(
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            throw new ForbiddenException("Для просмотра своих заявок необходимо войти в систему");
        }
        Pageable safePageable = PageRequests.of(pageable.getPageNumber(), pageable.getPageSize(),
                paginationConfig, pageable.getSort());
        Page<PostDto> posts = postService.getMyPosts(currentUser.getUserId(), safePageable);
        return ResponseEntity.ok(PageResponse.from(posts));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PostDto> updatePost(
            @PathVariable Long id,
            @Valid @RequestBody UpdatePostRequest request) {
        PostDto post = postService.getPostById(id);
        var currentUser = authService.getCurrentUser();
        log.debug("updatePost: postUserId={}, currentUserId={}, currentUser={}", 
                post.getUserId(), currentUser != null ? currentUser.getUserId() : null, currentUser);
        if (!privacyPolicy.canManagePost(post.getUserId())) {
            throw new ForbiddenException("Вы можете редактировать только свои посты");
        }
        return ResponseEntity.ok(postService.updatePost(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        PostDto post = postService.getPostById(id);
        if (!privacyPolicy.canManagePost(post.getUserId())) {
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
    @Operation(summary = "Заявки пользователя (постранично)")
    @SecurityRequirements
    public ResponseEntity<PageResponse<PublicPostDto>> getPostsByUser(
            @PathVariable Long userId,
            @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        boolean includeAllStatuses = privacyPolicy.canViewAllPostStatuses(userId);
        Pageable safePageable = PageRequests.of(pageable.getPageNumber(), pageable.getPageSize(),
                paginationConfig, pageable.getSort());
        Page<PublicPostDto> posts = postService.getPostsByUser(userId, includeAllStatuses, safePageable);
        return ResponseEntity.ok(PageResponse.from(posts));
    }

    @PostMapping("/by-ids")
    public ResponseEntity<List<PublicPostDto>> getPostsByIds(@RequestBody List<Long> ids) {
        if (ids.size() > 100) {
            throw new BadRequestException("Максимальный размер пакетного запроса — 100 ID");
        }
        return ResponseEntity.ok(postService.getPostsByIds(ids));
    }

    @GetMapping("/map")
    @Operation(summary = "Заявки на карте (постранично)",
            description = "Фильтры bbox или latitude/longitude + radius. Ответ — PageResponse.")
    @SecurityRequirements
    public ResponseEntity<PageResponse<PublicPostDto>> getPostsOnMap(
            @RequestParam(required = false) String bbox,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude,
            @RequestParam(required = false) Double radius,
            @RequestParam(required = false) PostStatus status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Bbox parsedBbox = parseBbox(bbox);
        Double effectiveRadius = radius;
        if (latitude != null && longitude != null && effectiveRadius == null) {
            effectiveRadius = 10.0;
        }

        Pageable pageable = PageRequests.of(page, size, paginationConfig, Sort.unsorted());
        Page<PublicPostDto> result = postService.getPostsOnMap(
                status, category, city, q,
                parsedBbox.minLat(), parsedBbox.minLng(), parsedBbox.maxLat(), parsedBbox.maxLng(),
                latitude, longitude, effectiveRadius,
                pageable);
        return ResponseEntity.ok(PageResponse.from(result));
    }

    private Bbox parseBbox(String bbox) {
        if (!StringUtils.hasText(bbox)) {
            return new Bbox(null, null, null, null);
        }
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw new BadRequestException("bbox должен быть в формате minLat,minLng,maxLat,maxLng");
        }
        try {
            return new Bbox(
                    Double.parseDouble(parts[0].trim()),
                    Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()),
                    Double.parseDouble(parts[3].trim()));
        } catch (NumberFormatException e) {
            throw new BadRequestException("bbox содержит нечисловые координаты");
        }
    }

    private record Bbox(Double minLat, Double minLng, Double maxLat, Double maxLng) {}

    @PostMapping("/{id}/boost")
    public ResponseEntity<Map<String, Object>> boostPost(
            @PathVariable Long id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        var currentUser = authService.getCurrentUser();
        if (currentUser == null) {
            throw new ForbiddenException("Необходимо авторизоваться");
        }
        
        var result = postService.boostPost(
                id,
                currentUser.getUserId(),
                "ADMIN".equals(currentUser.getRole()),
                idempotencyKey
        );
        return ResponseEntity.ok(Map.of(
            "success", true,
            "applied", result.applied(),
            "post", result.post(),
            "price", result.price(),
            "balance", result.balance(),
            "boostedUntil", result.boostedUntil(),
            "message", "Пост поднят в топ на " + economyConfig.getBoostHours() + " часов!"
        ));
    }
}