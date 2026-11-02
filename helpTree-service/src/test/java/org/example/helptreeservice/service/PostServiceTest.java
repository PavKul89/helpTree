package org.example.helptreeservice.service;

import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.dto.posts.PostDto;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.PostStatus;
import org.example.helptreeservice.enums.TransactionType;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.mapper.PostMapper;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostMapper postMapper;
    @Mock
    private GeocodingService geocodingService;
    @Mock
    private UserService userService;
    @Mock
    private ImageService imageService;
    @Mock
    private WalletService walletService;

    private EconomyConfig economyConfig;

    private PostService postService;

    private User owner;

    @BeforeEach
    void setUp() {
        economyConfig = new EconomyConfig();
        postService = new PostService(
                postRepository,
                userRepository,
                postMapper,
                geocodingService,
                userService,
                imageService,
                walletService,
                economyConfig
        );
        owner = user(1L, "Owner");

        lenient().when(postMapper.toDto(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            return PostDto.builder()
                    .id(post.getId())
                    .status(post.getStatus())
                    .userId(post.getUser() != null ? post.getUser().getId() : null)
                    .build();
        });
    }

    private User user(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setDeleted(false);
        return user;
    }

    private Post post(Long id, PostStatus status, User author) {
        Post post = new Post();
        post.setId(id);
        post.setTitle("Post " + id);
        post.setStatus(status);
        post.setUser(author);
        post.setDeleted(false);
        return post;
    }

    @Test
    void getPostsByUser_shouldHideCancelledPostsForPublicViewer() {
        when(userService.getUserEntityById(1L)).thenReturn(owner);
        when(postRepository.findByUserId(1L)).thenReturn(List.of(
                post(1L, PostStatus.OPEN, owner),
                post(2L, PostStatus.CANCELLED, owner),
                post(3L, PostStatus.COMPLETED, owner)
        ));

        List<PostDto> result = postService.getPostsByUser(1L, false);

        assertEquals(2, result.size());
        assertTrue(result.stream().noneMatch(dto -> dto.getStatus() == PostStatus.CANCELLED));
    }

    @Test
    void getPostsByUser_shouldShowAllNonDeletedPostsToOwnerOrAdmin() {
        when(userService.getUserEntityById(1L)).thenReturn(owner);
        when(postRepository.findByUserId(1L)).thenReturn(List.of(
                post(1L, PostStatus.OPEN, owner),
                post(2L, PostStatus.CANCELLED, owner),
                post(3L, PostStatus.COMPLETED, owner)
        ));

        List<PostDto> result = postService.getPostsByUser(1L, true);

        assertEquals(3, result.size());
        assertTrue(result.stream().anyMatch(dto -> dto.getStatus() == PostStatus.CANCELLED));
    }

    @Test
    void getPostsByUser_shouldRejectDeletedUser() {
        when(userService.getUserEntityById(1L))
                .thenThrow(new org.example.helptreeservice.exception.NotFoundException("Пользователь не найден"));

        assertThrows(org.example.helptreeservice.exception.NotFoundException.class,
                () -> postService.getPostsByUser(1L, false));
        verify(postRepository, never()).findByUserId(anyLong());
    }

    @Test
    void boostPost_shouldRejectNonOwner() {
        Post post = post(5L, PostStatus.OPEN, owner);
        when(postRepository.findByIdWithUser(5L)).thenReturn(post);

        assertThrows(ForbiddenException.class, () -> postService.boostPost(5L, 2L, false));
        verifyNoInteractions(walletService);
        verify(postRepository, never()).save(any());
    }

    @Test
    void boostPost_shouldRejectNonOpenPost() {
        Post post = post(5L, PostStatus.COMPLETED, owner);
        when(postRepository.findByIdWithUser(5L)).thenReturn(post);

        assertThrows(BadRequestException.class, () -> postService.boostPost(5L, 1L, false));
        verifyNoInteractions(walletService);
        verify(postRepository, never()).save(any());
    }

    @Test
    void boostPost_shouldChargeOwnerAndBoostOpenPost() {
        Post post = post(5L, PostStatus.OPEN, owner);
        when(postRepository.findByIdWithUser(5L)).thenReturn(post);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PostDto result = postService.boostPost(5L, 1L, false);

        assertEquals(5L, result.getId());
        verify(walletService).spendCoins(
                eq(1L),
                eq(economyConfig.getBoostPrice()),
                eq(TransactionType.POST_BOOST),
                contains("5")
        );
        assertNotNull(post.getBoostedUntil());
    }

    @Test
    void boostPost_shouldAllowAdminToBoostSomeoneElsesPost() {
        Post post = post(5L, PostStatus.OPEN, owner);
        when(postRepository.findByIdWithUser(5L)).thenReturn(post);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));

        postService.boostPost(5L, 99L, true);

        verify(walletService).spendCoins(eq(99L), anyLong(), eq(TransactionType.POST_BOOST), anyString());
    }
}
