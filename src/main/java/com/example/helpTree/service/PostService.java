package com.example.helpTree.service;

import com.example.helpTree.dto.CreatePostRequest;
import com.example.helpTree.dto.PostDto;
import com.example.helpTree.dto.UpdatePostRequest;
import com.example.helpTree.entity.Post;
import com.example.helpTree.entity.User;
import com.example.helpTree.repository.PostRepository;
import com.example.helpTree.repository.UserRepository;
import lombok.RequiredArgsConstructor;
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

    public PostDto createPost(CreatePostRequest request) {
        // Находим пользователя по ID
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Пользователь не найден с id: " + request.getUserId()));

        Post post = new Post();
        post.setUser(user);
        post.setTitle(request.getTitle());
        post.setDescription(request.getDescription());
        post.setAuthorName(request.getAuthorName());
        post.setCreatedAt(LocalDateTime.now());
        post.setUpdatedAt(LocalDateTime.now());

        Post savedPost = postRepository.save(post);
        return mapToDto(savedPost);
    }

    @Transactional(readOnly = true)
    public List<PostDto> getPostsByUser(Long userId) {
        return postRepository.findAll().stream()
                .filter(post -> post.getUser().getId().equals(userId))
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PostDto getPostById(Long id) {
        return mapToDto(getPostEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<PostDto> getAllPosts() {
        return postRepository.findAll().stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
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

        post.setUpdatedAt(LocalDateTime.now());
        Post updatedPost = postRepository.save(post);
        return mapToDto(updatedPost);
    }

    public void deletePost(Long id) {
        if (!postRepository.existsById(id)) {
            throw new RuntimeException("Пост не найден с id: " + id);
        }
        postRepository.deleteById(id);
    }

    private Post getPostEntityById(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Пост не найден с id: " + id));
    }

    private PostDto mapToDto(Post post) {
        return PostDto.builder()
                .id(post.getId())
                .title(post.getTitle())
                .description(post.getDescription())
                .authorName(post.getAuthorName())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .userId(post.getUser().getId())
                .userEmail(post.getUser().getEmail())
                .userRating(post.getUser().getRating())
                .build();
    }
}

