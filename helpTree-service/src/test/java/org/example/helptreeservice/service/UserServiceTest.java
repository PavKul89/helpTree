package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.users.CreateUserRequest;
import org.example.helptreeservice.dto.users.UpdateUserRequest;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.dto.users.UserPublicDto;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.UserMapper;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
    @Mock
    private AuthSessionService authSessionService;

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
                geocodingService,
                authSessionService
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

    // ---------------------------------------------------------------
    // Last-administrator protection
    // ---------------------------------------------------------------

    @Test
    void deleteUser_shouldRejectLastAdministrator() {
        User admin = user(1L, false);
        admin.setRole(Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findActiveByRoleForUpdate(Role.ADMIN)).thenReturn(List.of(admin));

        assertThrows(ConflictException.class, () -> userService.deleteUser(1L));

        verify(userRepository, never()).save(any());
        verify(authSessionService, never()).revokeAllSessionsForUser(anyLong(), anyString());
    }

    @Test
    void deleteUser_shouldAllowAdministratorWhenAnotherRemains() {
        User admin = user(1L, false);
        admin.setRole(Role.ADMIN);
        User peer = user(2L, false);
        peer.setRole(Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findActiveByRoleForUpdate(Role.ADMIN)).thenReturn(List.of(admin, peer));

        userService.deleteUser(1L);

        assertTrue(admin.getDeleted());
        verify(userRepository).save(admin);
        verify(authSessionService).revokeAllSessionsForUser(1L, "ACCOUNT_DELETED");
    }

    @Test
    void deleteUser_shouldRevokeSessionsOfRegularUser() {
        User user = user(1L, false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        userService.deleteUser(1L);

        assertTrue(user.getDeleted());
        verify(authSessionService).revokeAllSessionsForUser(1L, "ACCOUNT_DELETED");
    }

    @Test
    void changeRole_shouldRejectDemotingLastAdministrator() {
        User admin = user(1L, false);
        admin.setRole(Role.ADMIN);
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findActiveByRoleForUpdate(Role.ADMIN)).thenReturn(List.of(admin));

        assertThrows(ConflictException.class, () -> userService.changeRole(1L, Role.USER));

        assertEquals(Role.ADMIN, admin.getRole());
        verify(userRepository, never()).save(any());
        verify(authSessionService, never()).revokeAllSessionsForUser(anyLong(), anyString());
    }

    @Test
    void changeRole_shouldUpdateRoleAndRevokeOldSessions() {
        User user = user(1L, false);
        UserDto dto = new UserDto();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(dto);

        UserDto result = userService.changeRole(1L, Role.ADMIN);

        assertEquals(Role.ADMIN, user.getRole());
        assertEquals(dto, result);
        verify(userRepository).save(user);
        verify(authSessionService).revokeAllSessionsForUser(1L, "ROLE_CHANGED");
    }

    @Test
    void changeRole_shouldNotRevokeSessionsWhenRoleIsUnchanged() {
        User user = user(1L, false);
        UserDto dto = new UserDto();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(dto);

        UserDto result = userService.changeRole(1L, Role.USER);

        assertEquals(dto, result);
        verify(userRepository, never()).save(any());
        verify(authSessionService, never()).revokeAllSessionsForUser(anyLong(), anyString());
    }

    @Test
    void changeRole_shouldRejectDeletedUser() {
        User user = user(1L, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        assertThrows(NotFoundException.class, () -> userService.changeRole(1L, Role.ADMIN));
    }

    // ---------------------------------------------------------------
    // Profile email and registration
    // ---------------------------------------------------------------

    @Test
    void updateUser_shouldRejectEmailChangeThroughProfile() {
        User user = user(1L, false);
        user.setEmail("old@test.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("new@test.com");

        assertThrows(BadRequestException.class, () -> userService.updateUser(1L, request));

        assertEquals("old@test.com", user.getEmail());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUser_shouldAllowUnchangedEmailFromProfileForm() {
        User user = user(1L, false);
        user.setEmail("user@test.com");
        UserDto dto = new UserDto();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(dto);
        UpdateUserRequest request = new UpdateUserRequest();
        request.setEmail("USER@test.com");

        UserDto result = userService.updateUser(1L, request);

        assertEquals(dto, result);
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_shouldNormalizeEmailAndStartUnverified() {
        CreateUserRequest request = new CreateUserRequest();
        request.setName("New User");
        request.setEmail("  Mixed@Test.COM ");
        request.setPassword("Strong1Pass");
        request.setPhone("1234567890");
        request.setCity("Moscow");
        UserDto dto = new UserDto();

        when(userRepository.existsByEmailIgnoreCase("mixed@test.com")).thenReturn(false);
        when(passwordService.encode("Strong1Pass")).thenReturn("encoded-password");
        when(geocodingService.geocodeCity("Moscow")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(userMapper.toDto(any(User.class))).thenReturn(dto);

        UserDto result = userService.createUser(request);

        assertEquals(dto, result);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("mixed@test.com", captor.getValue().getEmail());
        assertFalse(captor.getValue().getEmailVerified());
        assertEquals(Role.USER, captor.getValue().getRole());
        verify(achievementService).awardAchievementOnRegistration(any(User.class));
    }

    @Test
    void createUserWithRole_shouldMarkAdministratorsVerified() {
        CreateUserRequest request = new CreateUserRequest();
        request.setName("Admin User");
        request.setEmail("admin2@test.com");
        request.setPassword("Strong1Pass");
        request.setPhone("1234567890");
        request.setCity("Moscow");

        when(userRepository.existsByEmailIgnoreCase("admin2@test.com")).thenReturn(false);
        when(passwordService.encode("Strong1Pass")).thenReturn("encoded-password");
        when(geocodingService.geocodeCity("Moscow")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(11L);
            return saved;
        });
        when(userMapper.toDto(any(User.class))).thenReturn(new UserDto());

        userService.createUserWithRole(request, Role.ADMIN);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertTrue(captor.getValue().getEmailVerified());
        assertEquals(Role.ADMIN, captor.getValue().getRole());
    }

    @Test
    void blockUsersWithDebt_shouldRevokeSessionsOfBlockedUser() {
        User user = user(1L, false);
        user.setDebtCount(6);
        when(userRepository.findUsersWithDebtToBlock(any(LocalDateTime.class)))
                .thenReturn(List.of(user));

        userService.blockUsersWithDebt();

        assertNotNull(user.getBlockedUntil());
        assertTrue(user.getBlockedUntil().isAfter(LocalDateTime.now()));
        verify(authSessionService).revokeAllSessionsForUser(1L, "ACCOUNT_BLOCKED");
    }
}
