package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.posts.CreatePostRequest;
import org.example.helptreeservice.dto.posts.PostDto;
import org.example.helptreeservice.dto.posts.UpdatePostRequest;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.PostMapper;
import org.example.helptreeservice.metrics.annotation.BusinessMetric;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.PostSpecification;
import org.example.helptreeservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
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

    @BusinessMetric(
            value = "post.created",
            tags = {"operation=create, type=write"}
    )
    public PostDto createPost(CreatePostRequest request, Long authorId) {
        log.info("Создание нового поста для пользователя с ID: {}", authorId);

        try {
            User user = userRepository.findById(authorId)
                    .orElseThrow(() -> new NotFoundException("Пользователь не найден с id: " + authorId));

            boolean isInGoodStanding = user.getHelpedCount() >= user.getDebtCount();
            if (user.getDebtCount() > 3 && !isInGoodStanding) {
                throw new BadRequestException("Сначала помогите другим! Ваш долг: " + user.getDebtCount() + ", помогли: " + user.getHelpedCount());
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
                geocodingService.geocodeCity(user.getCity())
                        .ifPresent(coords -> {
                            post.setLatitude(coords.lat());
                            post.setLongitude(coords.lng());
                        });
            }
            
            if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
                post.setImageUrls(request.getImageUrls());
            }

            Post savedPost = postRepository.save(post);
            log.info("Пост успешно создан с ID: {}, пользователь ID: {}", savedPost.getId(), user.getId());
            log.debug("Созданный пост: title={}, status={}", savedPost.getTitle(), savedPost.getStatus());

            return postMapper.toDto(savedPost);

        } catch (NotFoundException e) {
            log.warn("Не удалось создать пост: пользователь с ID {} не найден", authorId);
            throw e;
        } catch (Exception e) {
            log.error("Ошибка при создании поста для пользователя ID: {}", authorId, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<PostDto> getPostsByUser(Long userId) {
        log.info("Запрос всех постов пользователя с ID: {}", userId);

        try {
            List<PostDto> posts = postRepository.findByUserId(userId).stream()
                    .map(postMapper::toDto)
                    .collect(Collectors.toList());

            log.info("Найдено {} постов для пользователя с ID: {}", posts.size(), userId);
            log.debug("ID постов пользователя {}: {}", userId,
                    posts.stream().map(PostDto::getId).collect(Collectors.toList()));

            return posts;

        } catch (Exception e) {
            log.error("Ошибка при получении постов пользователя с ID: {}", userId, e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    @BusinessMetric(
            value = "post.retrieved",
            tags = {"operation=get, type=read"}
    )
    public PostDto getPostById(Long id) {
        log.info("Запрос поста по ID: {}", id);

        try {
            PostDto postDto = postMapper.toDto(getPostEntityById(id));
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
    public List<PostDto> getAllPosts() {
        log.info("Запрос списка всех активных постов");

        try {
            List<PostDto> posts = postRepository.findAllNotDeleted().stream()
                    .map(postMapper::toDto)
                    .collect(Collectors.toList());

            log.info("Получен список всех активных постов, количество: {}", posts.size());
            log.debug("ID всех постов: {}", posts.stream().map(PostDto::getId).collect(Collectors.toList()));

            return posts;

        } catch (Exception e) {
            log.error("Ошибка при получении списка всех постов", e);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Page<PostDto> getPosts(Long userId, PostStatus status, String title, String authorName, String category, String city, Pageable pageable) {
        log.info("Запрос постов с фильтрацией: userId={}, status={}, title={}, authorName={}, category={}, city={}, page={}, size={}",
                userId, status, title, authorName, category, city, pageable.getPageNumber(), pageable.getPageSize());

        try {
            Specification<Post> spec = PostSpecification.filter(userId, status, title, authorName, category, city);
            Page<PostDto> postsPage = postRepository.findAll(spec, pageable).map(postMapper::toDto);

            log.info("Найдено постов с фильтрацией: {}, всего страниц: {}",
                    postsPage.getTotalElements(), postsPage.getTotalPages());
            log.debug("Посты на текущей странице: {}",
                    postsPage.getContent().stream().map(PostDto::getId).collect(Collectors.toList()));

            return postsPage;

        } catch (Exception e) {
            log.error("Ошибка при поиске постов с фильтрацией", e);
            throw e;
        }
    }

    public PostDto updatePost(Long id, UpdatePostRequest request) {
        log.info("Обновление поста с ID: {}", id);
        log.debug("Данные для обновления: title={}, description={}, authorName={}, status={}",
                request.getTitle(), request.getDescription(), request.getAuthorName(), request.getStatus());

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

            if (request.getStatus() != null && request.getStatus() != post.getStatus()) {
                PostStatus oldStatus = post.getStatus();
                post.setStatus(request.getStatus());
                changed = true;
                log.debug("Изменен статус поста с {} на {}", oldStatus, request.getStatus());
            }

            if (request.getImageUrls() != null) {
                post.setImageUrls(request.getImageUrls());
                changed = true;
                log.debug("Обновлен список изображений");
            }

            if (changed) {
                post.setUpdatedAt(LocalDateTime.now());
                Post updatedPost = postRepository.saveAndFlush(post);
                log.info("Пост с ID {} успешно обновлен", id);
                log.debug("Обновленный пост: title={}, status={}", updatedPost.getTitle(), updatedPost.getStatus());
                return postMapper.toDto(updatedPost);
            } else {
                log.info("Нет изменений для обновления поста с ID: {}", id);
                return postMapper.toDto(post);
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
    public List<PostDto> getPostsByIds(List<Long> ids) {
        log.info("Запрос постов по списку ID: {}", ids);
        return postRepository.findByIdsWithUser(ids).stream()
                .map(postMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PostDto> getPostsOnMap(Double latitude, Double longitude, Double radius, String status) {
        log.info("Запрос всех активных постов для карты");
        List<Post> posts = postRepository.findAllActive();
        
        for (Post post : posts) {
            if ((post.getLatitude() == null || post.getLongitude() == null) && post.getUser() != null && post.getUser().getCity() != null) {
                final String city = post.getUser().getCity();
                geocodingService.geocodeCity(city).ifPresent(coords -> {
                    post.setLatitude(coords.lat());
                    post.setLongitude(coords.lng());
                });
            }
        }
        
        return posts.stream().map(postMapper::toDto).collect(Collectors.toList());
    }
}