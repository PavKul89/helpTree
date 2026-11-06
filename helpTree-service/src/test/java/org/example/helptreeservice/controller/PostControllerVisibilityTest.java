package org.example.helptreeservice.controller;

import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.posts.PostDto;
import org.example.helptreeservice.dto.posts.PublicPostDto;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.PostService;
import org.example.helptreeservice.service.PrivacyPolicy;
import org.example.helptreeservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostControllerVisibilityTest {

    @Mock
    private PostService postService;
    @Mock
    private AuthorizationService authService;
    @Mock
    private UserService userService;
    @Mock
    private PrivacyPolicy privacyPolicy;
    @Mock
    private EconomyConfig economyConfig;
    @Mock
    private PaginationConfig paginationConfig;

    @InjectMocks
    private PostController postController;

    private final Pageable pageable = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        lenient().when(postService.getPosts(
                any(), any(), any(), any(), any(), any(), any(Pageable.class), anyBoolean()))
                .thenReturn(Page.empty());
        lenient().when(privacyPolicy.canViewAllPostStatuses(any())).thenReturn(false);
        lenient().when(paginationConfig.getDefaultPageSize()).thenReturn(20);
        lenient().when(paginationConfig.getMaxPageSize()).thenReturn(100);
    }

    @Test
    void anonymousGeneralFeed_shouldExcludeCancelledPosts() {
        postController.getPosts(null, null, null, null, null, null, pageable);

        verify(postService).getPosts(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pageable), eq(true));
    }

    @Test
    void adminGeneralFeed_shouldSeeAllStatuses() {
        when(privacyPolicy.canViewAllPostStatuses(null)).thenReturn(true);

        postController.getPosts(null, null, null, null, null, null, pageable);

        verify(postService).getPosts(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pageable), eq(false));
    }

    @Test
    void ownerAuthorFeed_shouldSeeAllStatuses() {
        when(privacyPolicy.canViewAllPostStatuses(7L)).thenReturn(true);

        postController.getPosts(7L, null, null, null, null, null, pageable);

        verify(postService).getPosts(
                eq(7L), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pageable), eq(false));
    }

    @Test
    void foreignAuthorFeed_shouldExcludeCancelledPosts() {
        postController.getPosts(7L, null, null, null, null, null, pageable);

        verify(postService).getPosts(
                eq(7L), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pageable), eq(true));
    }

    @Test
    void generalFeed_shouldClampRequestedPageSize() {
        postController.getPosts(null, null, null, null, null, null, PageRequest.of(0, 5000));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(postService).getPosts(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(),
                captor.capture(), eq(true));
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void detail_shouldReturnTheFullProjectionToTheAuthor() {
        PostDto full = PostDto.builder().id(1L).userId(7L).build();
        when(postService.getPostById(1L)).thenReturn(full);
        when(privacyPolicy.canViewFullPost(7L)).thenReturn(true);

        var response = postController.getPostById(1L);

        assertEquals(full, response.getBody());
    }

    @Test
    void detail_shouldReturnThePublicProjectionToAStranger() {
        PostDto full = PostDto.builder().id(1L).userId(7L).build();
        PublicPostDto publicPost = PublicPostDto.builder().id(1L).userId(7L).build();
        when(postService.getPostById(1L)).thenReturn(full);
        when(privacyPolicy.canViewFullPost(7L)).thenReturn(false);
        when(postService.toPublicPost(full)).thenReturn(publicPost);

        var response = postController.getPostById(1L);

        assertInstanceOf(PublicPostDto.class, response.getBody());
    }

    @Test
    void myPosts_shouldRequireAuthentication() {
        when(authService.getCurrentUser()).thenReturn(null);

        assertThrows(ForbiddenException.class,
                () -> postController.getMyPosts(pageable));
    }

    @Test
    void myPosts_shouldUseTheCurrentUser() {
        when(authService.getCurrentUser())
                .thenReturn(new AuthorizationService.UserContext(7L, "USER", "u@test.local"));
        when(postService.getMyPosts(eq(7L), any(Pageable.class))).thenReturn(Page.empty());

        var response = postController.getMyPosts(pageable);

        assertEquals(200, response.getStatusCode().value());
        verify(postService).getMyPosts(eq(7L), any(Pageable.class));
    }
}
