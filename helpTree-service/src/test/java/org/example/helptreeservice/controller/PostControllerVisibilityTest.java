package org.example.helptreeservice.controller;

import org.example.helptreeservice.config.EconomyConfig;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.PostService;
import org.example.helptreeservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
    private EconomyConfig economyConfig;

    @InjectMocks
    private PostController postController;

    private final Pageable pageable = PageRequest.of(0, 20);

    @BeforeEach
    void setUp() {
        lenient().when(postService.getPosts(
                any(), any(), any(), any(), any(), any(), any(Pageable.class), anyBoolean()))
                .thenReturn(Page.empty());
        lenient().when(authService.canManageUser(any())).thenReturn(false);
        lenient().when(authService.isAdmin()).thenReturn(false);
    }

    @Test
    void anonymousGeneralFeed_shouldExcludeCancelledPosts() {
        postController.getPosts(null, null, null, null, null, null, pageable);

        verify(postService).getPosts(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pageable), eq(true));
    }

    @Test
    void adminGeneralFeed_shouldSeeAllStatuses() {
        when(authService.isAdmin()).thenReturn(true);

        postController.getPosts(null, null, null, null, null, null, pageable);

        verify(postService).getPosts(
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(pageable), eq(false));
    }

    @Test
    void ownerAuthorFeed_shouldSeeAllStatuses() {
        when(authService.canManageUser(7L)).thenReturn(true);

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
}
