package com.example.helpTree.service;

import com.example.helpTree.dto.posts.CreatePostRequest;
import com.example.helpTree.dto.posts.PostDto;
import com.example.helpTree.dto.posts.UpdatePostRequest;
import com.example.helpTree.entity.Post;
import com.example.helpTree.entity.User;
import com.example.helpTree.enums.PostStatus;
import com.example.helpTree.mapper.PostMapper;
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
    private final PostMapper postMapper;

    public PostDto createPost(CreatePostRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

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
        return postRepository.findAll().stream()
                .filter(post -> post.getUser().getId().equals(userId))
                .map(postMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PostDto getPostById(Long id) {
        return postMapper.toDto(getPostEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<PostDto> getAllPosts() {
        return postRepository.findAll().stream()
                .map(postMapper::toDto)
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
        // 👇 ДОБАВИТЬ
        if (request.getStatus() != null) {
            post.setStatus(request.getStatus());
        }

        post.setUpdatedAt(LocalDateTime.now());
        Post updatedPost = postRepository.save(post);
        return postMapper.toDto(updatedPost);
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
}
