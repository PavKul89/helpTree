package com.example.helpTree.service;

import com.example.helpTree.dto.posts.CreatePostRequest;
import com.example.helpTree.dto.posts.PostDto;
import com.example.helpTree.dto.posts.UpdatePostRequest;
import com.example.helpTree.entity.Post;
import com.example.helpTree.entity.User;
import com.example.helpTree.enums.PostStatus;
import com.example.helpTree.exception.ConflictException;
import com.example.helpTree.exception.NotFoundException;
import com.example.helpTree.mapper.PostMapper;
import com.example.helpTree.metrics.annotation.BusinessMetric;
import com.example.helpTree.repository.PostRepository;
import com.example.helpTree.repository.PostSpecification;
import com.example.helpTree.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostMapper postMapper;

    @BusinessMetric(
        value = "post.created",
        tags = {"operation=create, type=write"}
    )
    public PostDto createPost(CreatePostRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new NotFoundException("Пользователь не найден"));

        Post post = new Post();
        post.setUser(user);
        post.setTitle(request.getTitle());
        post.setDescription(request.getDescription());
        post.setAuthorName(request.getAuthorName());
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());

        // 👇 ДОБАВЬ ЭТУ СТРОКУ
        post.setStatus(PostStatus.OPEN);  // новый пост открыт для помощи

        Post savedPost = postRepository.save(post);
        return postMapper.toDto(savedPost);
    }

    @Transactional(readOnly = true)
    public List<PostDto> getPostsByUser(Long userId) {
        return postRepository.findByUserId(userId).stream()
                .filter(p -> p.getDeleted() == null || !p.getDeleted())
                .map(postMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @BusinessMetric(
            value = "post.retrieved",
            tags = {"operation=get, type=read"}
    )
    public PostDto getPostById(Long id) {
        return postMapper.toDto(getPostEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<PostDto> getAllPosts() {
        return postRepository.findAll().stream()
                .filter(p -> p.getDeleted() == null || !p.getDeleted())
                .map(postMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<PostDto> getPosts(Long userId, PostStatus status, String title, String authorName, Pageable pageable) {
        Specification<Post> spec = PostSpecification.filter(userId, status, title, authorName);
        return postRepository.findAll(spec, pageable).map(postMapper::toDto);
    }

    public PostDto updatePost(Long id, UpdatePostRequest request) {
        Post post = getPostEntityById(id);

        if (request.getTitle() != null) {
            post.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            post.setDescription(request.getDescription());
        }
        if (request.getAuthorName() != null) {
            post.setAuthorName(request.getAuthorName());
        }
        // 👇 ДОБАВИТЬ
        if (request.getStatus() != null) {
            post.setStatus(request.getStatus());
        }

        post.setUpdatedAt(LocalDateTime.now());
        Post updatedPost = postRepository.save(post);
        return postMapper.toDto(updatedPost);
    }

    public void deletePost(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пост не найден с id: " + id));

        if (post.getDeleted() != null && post.getDeleted()) {
            throw new ConflictException("Пост уже был удалён");
        }

        post.setDeleted(true);
        post.setDeletedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.save(post);
    }

    public void restorePost(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пост не найден с id: " + id));

        if (post.getDeleted() == null || !post.getDeleted()) {
            throw new ConflictException("Пост не удалён");
        }

        post.setDeleted(false);
        post.setDeletedAt(null);
        post.setUpdatedAt(LocalDateTime.now());
        postRepository.save(post);
    }

    private Post getPostEntityById(Long id) {
        Post post = postRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Пост не найден с id: " + id));
        if (post.getDeleted() != null && post.getDeleted()) {
            throw new NotFoundException("Пост не найден с id: " + id);
        }
        return post;
    }
}
