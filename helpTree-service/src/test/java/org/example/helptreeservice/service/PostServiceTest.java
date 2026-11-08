package org.example.helptreeservice.service;

import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.dto.posts.PostBoostResult;
import org.example.helptreeservice.dto.posts.PostDto;
import org.example.helptreeservice.dto.posts.PublicPostDto;
import org.example.helptreeservice.dto.wallet.WalletOperationResult;
import org.example.helptreeservice.entity.Post;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.AdminAuditAction;
import org.example.helptreeservice.enums.PostStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

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
    @Mock
    private PrivacyPolicy privacyPolicy;
    @Mock
    private AdminAuditService adminAuditService;

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
                economyConfig,
                privacyPolicy,
                adminAuditService
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
        lenient().when(postMapper.toPublicDto(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            return PublicPostDto.builder()
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
    void getPostsByUser_shouldMapRepositoryPage() {
        when(userService.getUserEntityById(1L)).thenReturn(owner);
        Pageable pageable = PageRequest.of(0, 20);
        when(postRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(
                        post(1L, PostStatus.OPEN, owner),
                        post(3L, PostStatus.COMPLETED, owner)
                ), pageable, 2));

        Page<PublicPostDto> result = postService.getPostsByUser(1L, false, pageable);

        assertEquals(2, result.getTotalElements());
        assertEquals(2, result.getContent().size());
        verify(postRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void getPostsByUser_shouldMapAllStatusesForOwner() {
        when(userService.getUserEntityById(1L)).thenReturn(owner);
        Pageable pageable = PageRequest.of(0, 20);
        when(postRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(
                        post(1L, PostStatus.OPEN, owner),
                        post(2L, PostStatus.CANCELLED, owner),
                        post(3L, PostStatus.COMPLETED, owner)
                ), pageable, 3));

        Page<PublicPostDto> result = postService.getPostsByUser(1L, true, pageable);

        assertEquals(3, result.getTotalElements());
        assertTrue(result.getContent().stream().anyMatch(dto -> dto.getStatus() == PostStatus.CANCELLED));
    }

    @Test
    void getPostsByUser_shouldRejectDeletedUser() {
        when(userService.getUserEntityById(1L))
                .thenThrow(new org.example.helptreeservice.exception.NotFoundException("Пользователь не найден"));

        assertThrows(org.example.helptreeservice.exception.NotFoundException.class,
                () -> postService.getPostsByUser(1L, false, PageRequest.of(0, 20)));
        verify(postRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void boostPost_shouldRejectNonOwner() {
        Post post = post(5L, PostStatus.OPEN, owner);
        when(postRepository.findByIdWithUser(5L)).thenReturn(post);

        assertThrows(ForbiddenException.class,
                () -> postService.boostPost(5L, 2L, false, "key-non-owner"));
        verifyNoInteractions(walletService);
        verify(postRepository, never()).applyBoost(anyLong(), anyLong(), anyBoolean(), any(), any(), anyInt());
    }

    @Test
    void boostPost_shouldRejectNonOpenPost() {
        Post post = post(5L, PostStatus.COMPLETED, owner);
        when(postRepository.findByIdWithUser(5L)).thenReturn(post);

        assertThrows(BadRequestException.class,
                () -> postService.boostPost(5L, 1L, false, "key-completed"));
        verifyNoInteractions(walletService);
        verify(postRepository, never()).applyBoost(anyLong(), anyLong(), anyBoolean(), any(), any(), anyInt());
    }

    @Test
    void boostPost_shouldChargeOwnerAndBoostOpenPost() {
        Post post = post(5L, PostStatus.OPEN, owner);
        when(postRepository.findByIdWithUser(5L)).thenReturn(post);
        when(walletService.chargeForPostBoost(1L, 5L, "key-owner"))
                .thenReturn(WalletOperationResult.builder()
                        .applied(true)
                        .price(5L)
                        .balance(10L)
                        .build());
        when(postRepository.applyBoost(
                eq(5L), eq(1L), eq(false), any(), any(), eq(economyConfig.getBoostHours())))
                .thenAnswer(invocation -> {
                    post.setBoostedUntil(invocation.getArgument(4));
                    return 1;
                });

        PostBoostResult result = postService.boostPost(5L, 1L, false, "key-owner");

        assertEquals(5L, result.post().getId());
        assertTrue(result.applied());
        assertEquals(5L, result.price());
        assertNotNull(result.boostedUntil());
        verify(walletService).chargeForPostBoost(1L, 5L, "key-owner");
    }

    @Test
    void boostPost_shouldNotExtendAgainOnIdempotentRetry() {
        Post post = post(5L, PostStatus.OPEN, owner);
        post.setBoostedUntil(java.time.LocalDateTime.now().plusHours(12));
        when(postRepository.findByIdWithUser(5L)).thenReturn(post);
        when(walletService.chargeForPostBoost(1L, 5L, "key-retry"))
                .thenReturn(WalletOperationResult.builder()
                        .applied(false)
                        .price(5L)
                        .balance(10L)
                        .build());

        PostBoostResult result = postService.boostPost(5L, 1L, false, "key-retry");

        assertFalse(result.applied());
        verify(postRepository, never()).applyBoost(anyLong(), anyLong(), anyBoolean(), any(), any(), anyInt());
        assertEquals(post.getBoostedUntil(), result.boostedUntil());
    }

    @Test
    void boostPost_shouldRollBackWhenPostIsNoLongerEligible() {
        Post post = post(5L, PostStatus.OPEN, owner);
        when(postRepository.findByIdWithUser(5L)).thenReturn(post);
        when(walletService.chargeForPostBoost(1L, 5L, "key-rollback"))
                .thenReturn(WalletOperationResult.builder()
                        .applied(true)
                        .price(5L)
                        .balance(10L)
                        .build());
        when(postRepository.applyBoost(
                eq(5L), eq(1L), eq(false), any(), any(), eq(economyConfig.getBoostHours())))
                .thenReturn(0);

        assertThrows(BadRequestException.class,
                () -> postService.boostPost(5L, 1L, false, "key-rollback"));
    }

    @Test
    void boostPost_shouldAllowAdminToBoostSomeoneElsesPost() {
        Post post = post(5L, PostStatus.OPEN, owner);
        when(postRepository.findByIdWithUser(5L)).thenReturn(post);
        when(walletService.chargeForPostBoost(99L, 5L, "key-admin"))
                .thenReturn(WalletOperationResult.builder()
                        .applied(true)
                        .price(5L)
                        .balance(10L)
                        .build());
        when(postRepository.applyBoost(
                eq(5L), eq(99L), eq(true), any(), any(), eq(economyConfig.getBoostHours())))
                .thenReturn(1);

        postService.boostPost(5L, 99L, true, "key-admin");

        verify(walletService).chargeForPostBoost(99L, 5L, "key-admin");
    }

    @Test
    void deletePost_adminOverload_shouldRequireReason() {
        assertThrows(BadRequestException.class, () -> postService.deletePost(5L, 9L, "  "));

        verifyNoInteractions(adminAuditService);
        verify(postRepository, never()).save(any(Post.class));
    }

    @Test
    void deletePost_adminOverload_shouldSoftDeleteAndWriteAudit() {
        Post post = post(5L, PostStatus.OPEN, owner);
        when(postRepository.findById(5L)).thenReturn(Optional.of(post));

        postService.deletePost(5L, 9L, "Нарушение правил");

        assertEquals(true, post.getDeleted());
        verify(adminAuditService).record(eq(9L), eq(AdminAuditAction.POST_DELETED),
                eq(AdminAuditService.ENTITY_POST), eq(5L), anyMap(), anyMap(),
                eq("Нарушение правил"));
    }

    @Test
    void changeStatus_shouldPersistAndAuditTransition() {
        Post post = post(5L, PostStatus.OPEN, owner);
        when(postRepository.findById(5L)).thenReturn(Optional.of(post));
        when(postRepository.saveAndFlush(post)).thenReturn(post);

        PostDto result = postService.changeStatus(5L, PostStatus.COMPLETED, 9L, "Ручное завершение");

        assertEquals(PostStatus.COMPLETED, result.getStatus());
        verify(adminAuditService).record(eq(9L), eq(AdminAuditAction.POST_STATUS_CHANGED),
                eq(AdminAuditService.ENTITY_POST), eq(5L), anyMap(), anyMap(),
                eq("Ручное завершение"));
    }
}
