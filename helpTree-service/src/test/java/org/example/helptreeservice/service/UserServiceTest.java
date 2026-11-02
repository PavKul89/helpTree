package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.users.UserPublicDto;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.UserMapper;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private PasswordService passwordService;
    @Mock
    private ImageService imageService;
    @Mock
    private AchievementService achievementService;
    @Mock
    private GeocodingService geocodingService;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(
                userRepository,
                postRepository,
                userMapper,
                passwordService,
                imageService,
                achievementService,
                geocodingService
        );
    }

    private User user(Long id, boolean deleted) {
        User user = new User();
        user.setId(id);
        user.setName("User " + id);
        user.setDeleted(deleted);
        user.setFavoritePostIds(new ArrayList<>());
        return user;
    }

    @Test
    void getFavorites_shouldRejectDeletedUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, true)));

        assertThrows(NotFoundException.class, () -> userService.getFavorites(1L));
        verify(userRepository, never()).findFavoritePostIds(anyLong());
    }

    @Test
    void isFavorite_shouldRejectDeletedUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, true)));

        assertThrows(NotFoundException.class, () -> userService.isFavorite(1L, 5L));
    }

    @Test
    void getTelegramChatId_shouldRejectDeletedUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, true)));

        assertThrows(NotFoundException.class, () -> userService.getTelegramChatId(1L));
    }

    @Test
    void addFavorite_shouldRejectMissingPostBeforeLockingUser() {
        when(postRepository.existsByIdAndDeletedFalse(5L)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> userService.addFavorite(1L, 5L));
        verify(userRepository, never()).findByIdForUpdate(anyLong());
        verify(userRepository, never()).save(any());
    }

    @Test
    void addFavorite_shouldRejectDeletedUser() {
        when(postRepository.existsByIdAndDeletedFalse(5L)).thenReturn(true);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user(1L, true)));

        assertThrows(NotFoundException.class, () -> userService.addFavorite(1L, 5L));
        verify(userRepository, never()).save(any());
    }

    @Test
    void addFavorite_shouldNotDuplicateAlreadyFavoritePost() {
        User user = user(1L, false);
        user.getFavoritePostIds().add(5L);
        when(postRepository.existsByIdAndDeletedFalse(5L)).thenReturn(true);
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        userService.addFavorite(1L, 5L);

        assertEquals(List.of(5L), user.getFavoritePostIds());
        verify(userRepository, never()).save(any());
    }

    @Test
    void getUsersPublicByIds_shouldExcludeDeletedUsers() {
        when(userRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(user(1L, false), user(2L, true)));

        List<UserPublicDto> result = userService.getUsersPublicByIds(List.of(1L, 2L));

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }
}
