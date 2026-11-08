package org.example.helptreeservice.service;

import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.dto.posts.CreatePostRequest;
import org.example.helptreeservice.dto.posts.PostBoostResult;
import org.example.helptreeservice.dto.posts.PostDto;
import org.example.helptreeservice.dto.posts.PublicPostDto;
import org.example.helptreeservice.dto.posts.UpdatePostRequest;
import org.example.helptreeservice.dto.wallet.WalletOperationResult;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.dto.admin.AdminBulkResultDto;
import org.example.helptreeservice.enums.AdminAuditAction;
import org.example.helptreeservice.enums.ImagePurpose;
import org.example.helptreeservice.enums.ModerationStatus;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.PostMapper;
import org.example.helptreeservice.metrics.annotation.BusinessMetric;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.PostSpecification;
import org.example.helptreeservice.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostMapper postMapper;
    private final GeocodingService geocodingService;
    private final UserService userService;
    private final ImageService imageService;
    private final WalletService walletService;
    private final EconomyConfig economyConfig;
    private final PrivacyPolicy privacyPolicy;
    private final AdminAuditService adminAuditService;

    private PostDto toDto(Post post) {
        PostDto dto = postMapper.toDto(post);
        dto.setImageUrls(imageService.refreshUrls(post.getImageUrls()));
        dto.setAuthorAvatarUrl(imageService.refreshUrl(dto.getAuthorAvatarUrl()));
        return dto;
    }

    /**
     * Public projection: no block flag, no boost expiry and coordinates only at
     * the privacy grid resolution. Every anonymous or non-owner caller goes
     * through this method.
     */
    private PublicPostDto toPublicDto(Post post) {
        PublicPostDto dto = postMapper.toPublicDto(post);
        dto.setImageUrls(imageService.refreshUrls(post.getImageUrls()));
        dto.setAuthorAvatarUrl(imageService.refreshUrl(dto.getAuthorAvatarUrl()));
        dto.setLatitude(privacyPolicy.publicCoordinate(dto.getLatitude()));
        dto.setLongitude(privacyPolicy.publicCoordinate(dto.getLongitude()));
        return dto;
    }

    /**
     * Projects an already loaded full DTO for a public response. Used by the
     * detail endpoint, which must decide per viewer whether the owner/admin
     * projection is allowed.
     */
    public PublicPostDto toPublicPost(PostDto post) {
        return PublicPostDto.builder()
                .id(post.getId())
                .title(post.getTitle())
                .description(post.getDescription())
                .category(post.getCategory())
                .authorName(post.getAuthorName())
                .authorAvatarUrl(post.getAuthorAvatarUrl())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .userId(post.getUserId())
                .authorRating(post.getAuthorRating())
                .userCity(post.getUserCity())
                .latitude(privacyPolicy.publicCoordinate(post.getLatitude()))
                .longitude(privacyPolicy.publicCoordinate(post.getLongitude()))
                .helperId(post.getHelperId())
                .helperName(post.getHelperName())
                .status(post.getStatus())
                .imageUrls(post.getImageUrls())
                .boosted(post.isBoosted())
                .authorNicknameColor(post.getAuthorNicknameColor())
                .commentsCount(post.getCommentsCount())
                .build();
    }

    @BusinessMetric(
            value = "post.created",
            tags = {"operation=create, type=write"}
    )
    public PostDto createPost(CreatePostRequest request, Long authorId) {
        log.info("Создание нового поста для пользователя с ID: {}", authorId);

        try {
            User user = userRepository.findById(authorId)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + authorId));

            if (userService.isUserBlocked(authorId)) {
                throw new BadRequestException("Ваш аккаунт заблокирован. Невозможно создать пост.");
            }

            log.debug("Найден пользователь для создания поста: email={}", user.getEmail());

            Post post = new Post();
            post.setUser(user);
            post.setTitle(request.getTitle());
            post.setDescription(request.getDescription());
            post.setCategory(request.getCategory());
            post.setAuthorName(user.getName());
            post.setCreatedAt(LocalDateTime.now());
            post.setUpdatedAt(LocalDateTime.now());
            post.setStatus(PostStatus.OPEN);
            
            if (user.getLatitude() != null && user.getLongitude() != null) {
                post.setLatitude(user.getLatitude());
                post.setLongitude(user.getLongitude());
            } else if (user.getCity() != null) {
                // Never call Nominatim from a write path: use the durable cache
                // when the city is already resolved and enqueue it otherwise.
                geocodingService.cached(user.getCity())
                        .ifPresent(coords -> {
                            post.setLatitude(coords.lat());
                            post.setLongitude(coords.lng());
                        });
                geocodingService.enqueue(user.getCity());
            }
            
            if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
                // Every attachment must be an active object uploaded by this
                // author. A client cannot inject somebody else's URL.
                imageService.claimOwnedImages(authorId, request.getImageUrls(), ImagePurpose.POST);
                post.setImageUrls(request.getImageUrls());
            }

            Post savedPost = postRepository.save(post);
            log.info("Пост успешно создан с ID: {}, пользователь ID: {}", savedPost.getId(), user.getId());
            log.debug("Созданный пост: title={}, status={}", savedPost.getTitle(), savedPost.getStatus());

            return toDto(savedPost);

        } catch (NotFoundException e) {
            log.warn("Не удалось создать пост: пользователь с ID {} не найден", authorId);
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при создании поста для пользователя ID: {}", authorId, e);
            throw e;
        }
    }

    /**
     * Public posts of one author. The owner/admin flag only widens the status
     * filter; the projection stays public because this is a public endpoint.
     */
    @Transactional(readOnly = true)
    public Page<PublicPostDto> getPostsByUser(Long userId, boolean includeAllStatuses, Pageable pageable) {
        log.info("Запрос постов пользователя с ID: {}, includeAllStatuses={}, page={}, size={}",
                userId, includeAllStatuses, pageable.getPageNumber(), pageable.getPageSize());

        try {
            userService.getUserEntityById(userId);

            Specification<Post> spec = PostSpecification.filter(
                    userId, null, null, null, null, null, !includeAllStatuses, !includeAllStatuses);

            Page<PublicPostDto> posts = postRepository.findAll(spec, pageable).map(this::toPublicDto);

            log.info("Найдено {} постов для пользователя с ID: {}", posts.getTotalElements(), userId);
            log.debug("ID постов пользователя {} на текущей странице: {}", userId,
                    posts.getContent().stream().map(PublicPostDto::getId).collect(Collectors.toList()));

            return posts;

        } catch (NotFoundException e) {
            log.warn("Пользователь с ID {} не найден или удалён", userId);
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при получении постов пользователя с ID: {}", userId, e);
            throw e;
        }
    }

    /**
     * Authenticated owner view: every non-deleted post of the current user in
     * every status, with the full projection. Reached only through
     * {@code GET /api/posts/mine}; never through a public path.
     */
    @Transactional(readOnly = true)
    public Page<PostDto> getMyPosts(Long userId, Pageable pageable) {
        log.info("Запрос собственных постов пользователя с ID: {}, page={}, size={}",
                userId, pageable.getPageNumber(), pageable.getPageSize());

        userService.getUserEntityById(userId);

        Specification<Post> spec = PostSpecification.filter(
                userId, null, null, null, null, null, false, false);

        return postRepository.findAll(spec, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    @BusinessMetric(
            value = "post.retrieved",
            tags = {"operation=get, type=read"}
    )
    public PostDto getPostById(Long id) {
        log.info("Запрос поста по ID: {}", id);

        try {
            Post post = getPostEntityById(id);
            // A moderated post is hidden from everyone except its author and
            // administrators. The same lookup is reused by owner-only flows, so
            // the check is based on the viewer, not on the endpoint.
            if (post.getModerationStatus() != ModerationStatus.ACTIVE
                    && !privacyPolicy.canViewFullPost(post.getUser() == null ? null : post.getUser().getId())) {
                throw new NotFoundException("Пост не найден с id: " + id);
            }
            PostDto postDto = toDto(post);
            log.info("Пост с ID {} найден: title={}, автор={}", id, postDto.getTitle(), postDto.getAuthorName());
            return postDto;

        } catch (NotFoundException e) {
            log.warn("Пост с ID {} не найден", id);
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при получении поста с ID: {}", id, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Page<PublicPostDto> getPosts(Long userId, PostStatus status, String title, String authorName, String category, String city, Pageable pageable, boolean excludeCancelled) {
        log.info("Запрос постов с фильтрацией: userId={}, status={}, title={}, authorName={}, category={}, city={}, page={}, size={}, excludeCancelled={}",
                userId, status, title, authorName, category, city, pageable.getPageNumber(), pageable.getPageSize(), excludeCancelled);

        try {
            if (userId != null) {
                userService.getUserEntityById(userId);
            }

            Specification<Post> spec = PostSpecification.filter(userId, status, title, authorName, category, city, excludeCancelled, true);
            
            Specification<Post> boostedSpec = (root, query, cb) -> {
                LocalDateTime now = LocalDateTime.now();
                var boostedOrder = cb.selectCase()
                    .when(cb.greaterThan(root.get("boostedUntil"), now), 1)
                    .otherwise(0);
                query.orderBy(
                    cb.desc(boostedOrder),
                    cb.desc(root.get("createdAt"))
                );
                return spec.toPredicate(root, query, cb);
            };
            
            Page<PublicPostDto> postsPage = postRepository.findAll(boostedSpec, pageable).map(this::toPublicDto);

            log.info("Найдено постов с фильтрацией: {}, всего страниц: {}",
                    postsPage.getTotalElements(), postsPage.getTotalPages());
            log.debug("Посты на текущей странице: {}",
                    postsPage.getContent().stream().map(PublicPostDto::getId).collect(Collectors.toList()));

            return postsPage;

        } catch (Exception e) {
            log.error("Ошибка при поиске постов с фильтрацией", e);
            throw e;
        }
    }

    public PostDto updatePost(Long id, UpdatePostRequest request) {
        log.info("Обновление поста с ID: {}", id);
        log.debug("Данные для обновления: title={}, description={}, authorName={}",
                request.getTitle(), request.getDescription(), request.getAuthorName());

        try {
            Post post = getPostEntityById(id);
            log.debug("Текущее состояние поста: title={}, status={}", post.getTitle(), post.getStatus());

            boolean changed = false;

            if (request.getTitle() != null && !request.getTitle().equals(post.getTitle())) {
                post.setTitle(request.getTitle());
                changed = true;
                log.debug("Изменен заголовок поста на: {}", request.getTitle());
            }

            if (request.getDescription() != null && !request.getDescription().equals(post.getDescription())) {
                post.setDescription(request.getDescription());
                changed = true;
                log.debug("Изменено описание поста");
            }

            if (request.getAuthorName() != null && !request.getAuthorName().equals(post.getAuthorName())) {
                post.setAuthorName(request.getAuthorName());
                changed = true;
                log.debug("Изменено имя автора на: {}", request.getAuthorName());
            }

            if (request.getImageUrls() != null) {
                if (!request.getImageUrls().isEmpty()) {
                    imageService.claimOwnedImages(
                            post.getUser().getId(), request.getImageUrls(), ImagePurpose.POST);
                }
                post.setImageUrls(request.getImageUrls());
                changed = true;
                log.debug("Обновлен список изображений");
            }

            if (changed) {
                post.setUpdatedAt(LocalDateTime.now());
                Post updatedPost = postRepository.saveAndFlush(post);
                log.info("Пост с ID {} успешно обновлен", id);
                log.debug("Обновленный пост: title={}, status={}", updatedPost.getTitle(), updatedPost.getStatus());
                return toDto(updatedPost);
            } else {
                log.info("Нет изменений для обновления поста с ID: {}", id);
                return toDto(post);
            }

        } catch (NotFoundException e) {
            log.warn("Не удалось обновить пост: ID {} не найден", id);
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при обновлении поста с ID: {}", id, e);
            throw e;
        }
    }

    public void deletePost(Long id) {
        log.info("Запрос на мягкое удаление поста с ID: {}", id);

        try {
            Post post = postRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Пост не найден с id: " + id));

            if (post.getDeleted() != null && post.getDeleted()) {
                log.warn("Попытка удалить уже удаленный пост с ID: {}", id);
                throw new ConflictException("Пост уже был удалён");
            }

            post.setDeleted(true);
            post.setDeletedAt(LocalDateTime.now());
            post.setUpdatedAt(LocalDateTime.now());
            postRepository.save(post);

            log.info("Пост с ID: {} успешно помечен как удаленный", id);
            log.debug("Детали удаленного поста: title={}, время удаления={}",
                    post.getTitle(), post.getDeletedAt());

        } catch (NotFoundException e) {
            log.warn("Не удалось удалить пост: ID {} не найден", id);
            throw e;
        } catch (ConflictException e) {
            log.warn("Не удалось удалить пост: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при удалении поста с ID: {}", id, e);
            throw e;
        }
    }

    public void restorePost(Long id) {
        log.info("Запрос на восстановление поста с ID: {}", id);

        try {
            Post post = postRepository.findById(id)
                    .orElseThrow(() -> new NotFoundException("Пост не найден с id: " + id));

            if (post.getDeleted() == null || !post.getDeleted()) {
                log.warn("Попытка восстановить не удаленный пост с ID: {}", id);
                throw new ConflictException("Пост не удалён");
            }

            post.setDeleted(false);
            post.setDeletedAt(null);
            post.setUpdatedAt(LocalDateTime.now());
            postRepository.save(post);

            log.info("Пост с ID: {} успешно восстановлен", id);
            log.debug("Детали восстановленного поста: title={}", post.getTitle());

        } catch (NotFoundException e) {
            log.warn("Не удалось восстановить пост: ID {} не найден", id);
            throw e;
        } catch (ConflictException e) {
            log.warn("Не удалось восстановить пост: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при восстановлении поста с ID: {}", id, e);
            throw e;
        }
    }

    // ------------------------------------------------------- administrative

    /**
     * Administrative soft delete with a mandatory reason. The owner-facing
     * overload above keeps its historical behaviour; this one additionally
     * records who deleted the request and why.
     */
    @Transactional
    public void deletePost(Long id, Long adminId, String reason) {
        String auditedReason = requireReason(reason);
        Post snapshot = postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пост не найден с id: " + id));
        Map<String, Object> before = postState(snapshot);

        deletePost(id);

        adminAuditService.record(adminId, AdminAuditAction.POST_DELETED,
                AdminAuditService.ENTITY_POST, id,
                before, Map.of("deleted", true), auditedReason);
    }

    @Transactional
    public void restorePost(Long id, Long adminId, String reason) {
        String auditedReason = requireReason(reason);
        Post snapshot = postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пост не найден с id: " + id));
        Map<String, Object> before = postState(snapshot);

        restorePost(id);

        Post restored = postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пост не найден с id: " + id));
        adminAuditService.record(adminId, AdminAuditAction.POST_RESTORED,
                AdminAuditService.ENTITY_POST, id,
                before, postState(restored), auditedReason);
    }

    /** Administrative status transition with a mandatory reason. */
    @Transactional
    public PostDto changeStatus(Long id, PostStatus newStatus, Long adminId, String reason) {
        String auditedReason = requireReason(reason);
        if (newStatus == null) {
            throw new BadRequestException("Статус обязателен");
        }

        Post post = postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пост не найден с id: " + id));
        if (Boolean.TRUE.equals(post.getDeleted())) {
            throw new NotFoundException("Пост не найден с id: " + id);
        }

        PostStatus previous = post.getStatus();
        if (previous == newStatus) {
            return toDto(post);
        }

        post.setStatus(newStatus);
        post.setUpdatedAt(LocalDateTime.now());
        Post saved = postRepository.saveAndFlush(post);

        adminAuditService.record(adminId, AdminAuditAction.POST_STATUS_CHANGED,
                AdminAuditService.ENTITY_POST, id,
                Map.of("status", previous.name()),
                Map.of("status", newStatus.name()),
                auditedReason);
        return toDto(saved);
    }

    /** Server-side list of soft-deleted requests, including anonymized rows. */
    @Transactional(readOnly = true)
    public Page<PostDto> getDeletedPosts(String q, String category, Pageable pageable) {
        String pattern = StringUtils.hasText(q)
                ? "%" + q.trim().toLowerCase(Locale.ROOT) + "%"
                : null;
        Specification<Post> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("deleted")));
            if (pattern != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(root.get("authorName")), pattern)));
            }
            if (StringUtils.hasText(category)) {
                predicates.add(cb.equal(root.get("category"), category.trim()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return postRepository.findAll(spec, pageable).map(this::toDto);
    }

    /**
     * Applies a bulk action item by item. A failing row is reported without
     * rolling back the successful ones; every successful item is already
     * audited by the corresponding single-item method.
     */
    @Transactional
    public AdminBulkResultDto bulk(String action,
                                   List<Long> ids,
                                   PostStatus status,
                                   Long adminId,
                                   String reason) {
        if (ids == null || ids.isEmpty()) {
            throw new BadRequestException("Список заявок не может быть пустым");
        }
        String operation = action == null ? "" : action.trim().toUpperCase(Locale.ROOT);
        if (!List.of("DELETE", "RESTORE", "STATUS").contains(operation)) {
            throw new BadRequestException("Поддерживаются операции DELETE, RESTORE и STATUS");
        }
        if ("STATUS".equals(operation) && status == null) {
            throw new BadRequestException("Для операции STATUS нужен статус");
        }
        String auditedReason = requireReason(reason);

        List<AdminBulkResultDto.Item> items = new ArrayList<>();
        int succeeded = 0;
        for (Long id : ids) {
            try {
                switch (operation) {
                    case "DELETE" -> deletePost(id, adminId, auditedReason);
                    case "RESTORE" -> restorePost(id, adminId, auditedReason);
                    case "STATUS" -> changeStatus(id, status, adminId, auditedReason);
                    default -> throw new BadRequestException("Неподдерживаемая операция");
                }
                succeeded++;
                items.add(AdminBulkResultDto.Item.builder().id(id).success(true).build());
            } catch (RuntimeException ex) {
                items.add(AdminBulkResultDto.Item.builder()
                        .id(id).success(false).error(ex.getMessage()).build());
            }
        }

        adminAuditService.record(adminId, AdminAuditAction.BULK_OPERATION,
                AdminAuditService.ENTITY_POST, null,
                null,
                Map.of("operation", operation, "requested", ids.size(), "succeeded", succeeded),
                auditedReason);

        return AdminBulkResultDto.builder()
                .action(operation)
                .requested(ids.size())
                .succeeded(succeeded)
                .failed(ids.size() - succeeded)
                .items(items)
                .build();
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Причина административного действия обязательна");
        }
        return reason.trim();
    }

    private Map<String, Object> postState(Post post) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("id", post.getId());
        state.put("title", post.getTitle());
        state.put("status", post.getStatus() != null ? post.getStatus().name() : null);
        state.put("deleted", post.getDeleted());
        state.put("deletedAt", post.getDeletedAt() != null ? post.getDeletedAt().toString() : null);
        return state;
    }

    private Post getPostEntityById(Long id) {
        log.debug("Поиск сущности поста по ID: {}", id);

        Post post = postRepository.findByIdWithUser(id);
        
        if (post == null) {
            log.debug("Пост с ID {} не найден", id);
            throw new NotFoundException("Пост не найден с id: " + id);
        }

        if (post.getDeleted() != null && post.getDeleted()) {
            log.debug("Пост с ID {} найден, но помечен как удаленный", id);
            throw new NotFoundException("Пост не найден с id: " + id);
        }

        log.debug("Сущность поста с ID {} успешно найдена", id);
        return post;
    }

    @Transactional(readOnly = true)
    public List<PublicPostDto> getPostsByIds(List<Long> ids) {
        log.info("Запрос постов по списку ID: {}", ids);
        return postRepository.findByIdsWithUser(ids).stream()
                .map(this::toPublicDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<PublicPostDto> getPostsOnMap(PostStatus status,
                                       String category,
                                       String city,
                                       String search,
                                       Double minLat,
                                       Double minLng,
                                       Double maxLat,
                                       Double maxLng,
                                       Double latitude,
                                       Double longitude,
                                       Double radius,
                                       Pageable pageable) {
        log.info("Запрос постов для карты: status={}, category={}, city={}, q={}, bbox=[{},{},{},{}], center=({},{}), radius={}, page={}, size={}",
                status, category, city, search, minLat, minLng, maxLat, maxLng,
                latitude, longitude, radius, pageable.getPageNumber(), pageable.getPageSize());

        validateMapBounds(minLat, minLng, maxLat, maxLng, latitude, longitude, radius);

        Double effectiveMinLat = minLat;
        Double effectiveMinLng = minLng;
        Double effectiveMaxLat = maxLat;
        Double effectiveMaxLng = maxLng;

        // A radius is translated into an indexed bounding box; the haversine
        // predicate then trims the box corners to a circle.
        if (latitude != null && longitude != null && radius != null && radius > 0) {
            double latDelta = Math.toDegrees(radius / 6371.0);
            double cosLat = Math.cos(Math.toRadians(latitude));
            double lngDelta = cosLat < 1.0e-6
                    ? 180.0
                    : Math.toDegrees(radius / (6371.0 * cosLat));
            effectiveMinLat = Math.max(-90.0, latitude - latDelta);
            effectiveMaxLat = Math.min(90.0, latitude + latDelta);
            effectiveMinLng = Math.max(-180.0, longitude - lngDelta);
            effectiveMaxLng = Math.min(180.0, longitude + lngDelta);
        }

        Specification<Post> mapSpec = PostSpecification.mapFilter(
                status, category, city, search,
                effectiveMinLat, effectiveMinLng, effectiveMaxLat, effectiveMaxLng,
                latitude, longitude, radius);

        Specification<Post> orderedSpec = (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                LocalDateTime now = LocalDateTime.now();
                var boostedOrder = cb.selectCase()
                        .when(cb.greaterThan(root.get("boostedUntil"), now), 1)
                        .otherwise(0);
                query.orderBy(
                        cb.desc(boostedOrder),
                        cb.desc(root.get("createdAt")),
                        cb.desc(root.get("id"))
                );
            }
            return mapSpec.toPredicate(root, query, cb);
        };

        Page<PublicPostDto> posts = postRepository.findAll(orderedSpec, pageable).map(this::toPublicDto);
        log.info("Найдено постов для карты: {}, всего страниц: {}", posts.getTotalElements(), posts.getTotalPages());
        return posts;
    }

    private void validateMapBounds(Double minLat,
                                   Double minLng,
                                   Double maxLat,
                                   Double maxLng,
                                   Double latitude,
                                   Double longitude,
                                   Double radius) {
        boolean anyBbox = minLat != null || minLng != null || maxLat != null || maxLng != null;
        boolean completeBbox = minLat != null && minLng != null && maxLat != null && maxLng != null;
        if (anyBbox && !completeBbox) {
            throw new BadRequestException("bbox должен содержать minLat, minLng, maxLat и maxLng");
        }
        if (completeBbox) {
            if (minLat < -90 || maxLat > 90 || minLng < -180 || maxLng > 180) {
                throw new BadRequestException("bbox содержит координаты вне допустимого диапазона");
            }
            if (minLat > maxLat || minLng > maxLng) {
                throw new BadRequestException("bbox должен быть задан как minLat,minLng,maxLat,maxLng");
            }
        }

        boolean anyCenter = latitude != null || longitude != null || radius != null;
        boolean completeCenter = latitude != null && longitude != null && radius != null;
        if (anyCenter && !completeCenter) {
            throw new BadRequestException("Для поиска по радиусу нужны latitude, longitude и radius");
        }
        if (completeCenter) {
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
                throw new BadRequestException("Координаты центра вне допустимого диапазона");
            }
            if (radius <= 0) {
                throw new BadRequestException("Параметр radius должен быть положительным");
            }
        }
    }

    /**
     * Boosts a post in the same transaction that debits the payer. The time
     * update is a single atomic UPDATE, so concurrent boosts extend each other
     * instead of overwriting the later expiry. A failed update rolls the debit
     * back automatically (there is no separate refund step to forget).
     */
    @Transactional
    public PostBoostResult boostPost(Long id, Long payerId, boolean privileged, String idempotencyKey) {
        log.info("Запрос на поднятие поста в топ, ID: {}, payerId: {}", id, payerId);

        Post post = getPostEntityById(id);

        if (!post.getUser().getId().equals(payerId) && !privileged) {
            throw new ForbiddenException("Вы можете поднять только свои посты");
        }

        if (post.getStatus() != PostStatus.OPEN) {
            throw new BadRequestException("Можно поднять только открытый пост");
        }

        WalletOperationResult charge = walletService.chargeForPostBoost(payerId, id, idempotencyKey);

        if (!Boolean.TRUE.equals(charge.getApplied())) {
            // Idempotent retry: the first request already extended the boost.
            Post current = getPostEntityById(id);
            return new PostBoostResult(
                    toDto(current),
                    charge.getPrice() != null ? charge.getPrice() : 0L,
                    charge.getBalance() != null ? charge.getBalance() : 0L,
                    false,
                    current.getBoostedUntil());
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime newBoostUntil = now.plusHours(economyConfig.getBoostHours());
        int updated = postRepository.applyBoost(
                id,
                payerId,
                privileged,
                now,
                newBoostUntil,
                economyConfig.getBoostHours());

        if (updated == 0) {
            // The post stopped being eligible after the initial check. Throwing
            // rolls back the debit, so no coins are lost.
            throw new BadRequestException("Пост больше нельзя поднять. Монеты не списаны.");
        }

        Post boosted = getPostEntityById(id);
        log.info("Пост {} поднят в топ до {}", id, boosted.getBoostedUntil());
        return new PostBoostResult(
                toDto(boosted),
                charge.getPrice() != null ? charge.getPrice() : 0L,
                charge.getBalance() != null ? charge.getBalance() : 0L,
                true,
                boosted.getBoostedUntil());
    }

    @Transactional
    public int clearExpiredBoosts() {
        int cleared = postRepository.clearExpiredBoosts(LocalDateTime.now());
        if (cleared > 0) {
            log.info("Снято истёкших boost: {}", cleared);
        }
        return cleared;
    }
}