package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.users.BlockUserRequest;
import org.example.helptreeservice.dto.users.BlockedUserResponse;
import org.example.helptreeservice.dto.users.UserBlockResponse;
import org.example.helptreeservice.dto.users.UserBlockStatusResponse;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.entity.UserBlockRelation;
import org.example.helptreeservice.enums.UserBlockScope;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.UserBlockRelationRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserBlockServiceTest {

    @Mock
    private UserBlockRelationRepository blockRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ImageService imageService;

    private UserBlockService service;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        service = new UserBlockService(blockRepository, userRepository, imageService);
        alice = user(1L, "Alice");
        bob = user(2L, "Bob");
    }

    @Test
    void blockUser_shouldRejectSelf() {
        assertThrows(BadRequestException.class,
                () -> service.blockUser(1L, 1L, new BlockUserRequest()));

        verify(blockRepository, never()).upsert(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void blockUser_shouldRejectMissingTarget() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> service.blockUser(1L, 2L, new BlockUserRequest()));
    }

    @Test
    void blockUser_shouldRejectExpiryInThePast() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));

        BlockUserRequest request = new BlockUserRequest();
        request.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        assertThrows(BadRequestException.class, () -> service.blockUser(1L, 2L, request));
        verify(blockRepository, never()).upsert(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void blockUser_shouldDefaultToPermanentAllScope() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        LocalDateTime now = LocalDateTime.now();
        UserBlockRelation relation = relation(10L, alice, bob, UserBlockScope.ALL, now, null);
        when(blockRepository.findByBlockerIdAndBlockedId(1L, 2L)).thenReturn(Optional.of(relation));

        UserBlockResponse response = service.blockUser(1L, 2L, null);

        ArgumentCaptor<String> scope = ArgumentCaptor.forClass(String.class);
        verify(blockRepository).upsert(eq(1L), eq(2L), scope.capture(), any(), eq(null));
        assertEquals("ALL", scope.getValue());
        assertEquals(10L, response.getId());
        assertEquals(UserBlockScope.ALL, response.getScope());
        assertTrue(response.isActive());
    }

    @Test
    void blockUser_shouldStoreRequestedScopeAndExpiry() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(3);
        UserBlockRelation relation = relation(11L, alice, bob, UserBlockScope.MESSAGES,
                LocalDateTime.now(), expiresAt);
        when(blockRepository.findByBlockerIdAndBlockedId(1L, 2L)).thenReturn(Optional.of(relation));

        BlockUserRequest request = new BlockUserRequest();
        request.setScope(UserBlockScope.MESSAGES);
        request.setExpiresAt(expiresAt);

        UserBlockResponse response = service.blockUser(1L, 2L, request);

        assertEquals(UserBlockScope.MESSAGES, response.getScope());
        assertEquals(expiresAt, response.getExpiresAt());
    }

    @Test
    void unblockUser_shouldBeIdempotent() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(blockRepository.deleteByBlockerIdAndBlockedId(1L, 2L)).thenReturn(0L);

        service.unblockUser(1L, 2L);

        verify(blockRepository).deleteByBlockerIdAndBlockedId(1L, 2L);
    }

    @Test
    void unblockUser_shouldRejectMissingTarget() {
        when(userRepository.findById(2L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.unblockUser(1L, 2L));
        verify(blockRepository, never()).deleteByBlockerIdAndBlockedId(anyLong(), anyLong());
    }

    @Test
    void getBlockedUsers_shouldReturnOnlyActiveRows() {
        LocalDateTime now = LocalDateTime.now();
        UserBlockRelation relation = relation(10L, alice, bob, UserBlockScope.ALL, now, null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(blockRepository.findActiveByBlockerId(eq(1L), any())).thenReturn(List.of(relation));
        when(imageService.refreshUrl(null)).thenReturn(null);

        List<BlockedUserResponse> blocked = service.getBlockedUsers(1L);

        assertEquals(1, blocked.size());
        assertEquals(2L, blocked.get(0).getUserId());
        assertEquals("Bob", blocked.get(0).getName());
    }

    @Test
    void getBlockStatus_shouldReportBothDirections() {
        LocalDateTime now = LocalDateTime.now();
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(blockRepository.findByBlockerIdAndBlockedId(1L, 2L))
                .thenReturn(Optional.of(relation(10L, alice, bob, UserBlockScope.MESSAGES, now, null)));
        when(blockRepository.findByBlockerIdAndBlockedId(2L, 1L))
                .thenReturn(Optional.of(relation(11L, bob, alice, UserBlockScope.PROFILE, now, null)));

        UserBlockStatusResponse status = service.getBlockStatus(1L, 2L);

        assertTrue(status.isBlockedByMe());
        assertTrue(status.isBlockedMe());
        assertTrue(status.isActive());
        // The viewer's own scope wins for display.
        assertEquals(UserBlockScope.MESSAGES, status.getScope());
    }

    @Test
    void getBlockStatus_shouldIgnoreExpiredRows() {
        LocalDateTime now = LocalDateTime.now();
        when(userRepository.findById(2L)).thenReturn(Optional.of(bob));
        when(blockRepository.findByBlockerIdAndBlockedId(1L, 2L))
                .thenReturn(Optional.of(relation(10L, alice, bob, UserBlockScope.ALL,
                        now.minusDays(2), now.minusDays(1))));
        when(blockRepository.findByBlockerIdAndBlockedId(2L, 1L)).thenReturn(Optional.empty());

        UserBlockStatusResponse status = service.getBlockStatus(1L, 2L);

        assertFalse(status.isBlockedByMe());
        assertFalse(status.isActive());
        assertNull(status.getScope());
    }

    @Test
    void getBlockStatus_shouldBeEmptyForSelf() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));

        UserBlockStatusResponse status = service.getBlockStatus(1L, 1L);

        assertFalse(status.isActive());
        assertFalse(status.isBlockedByMe());
        assertFalse(status.isBlockedMe());
    }

    @Test
    void isInteractionBlocked_shouldTreatSelfAsFree() {
        assertFalse(service.isInteractionBlocked(1L, 1L, UserBlockScope.ALL));
        verify(blockRepository, never()).existsEffectiveBetween(anyLong(), anyLong(), any(), any());
    }

    @Test
    void isInteractionBlocked_shouldMapNarrowScopeToAllAndNarrowScope() {
        when(blockRepository.existsEffectiveBetween(eq(1L), eq(2L), any(), any())).thenReturn(true);

        assertTrue(service.isInteractionBlocked(1L, 2L, UserBlockScope.MESSAGES));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UserBlockScope>> scopes = ArgumentCaptor.forClass(Collection.class);
        verify(blockRepository).existsEffectiveBetween(eq(1L), eq(2L), scopes.capture(), any());
        assertTrue(scopes.getValue().contains(UserBlockScope.MESSAGES));
        assertTrue(scopes.getValue().contains(UserBlockScope.ALL));
    }

    @Test
    void isInteractionBlocked_shouldOnlyUseAllForFullBlock() {
        when(blockRepository.existsEffectiveBetween(eq(1L), eq(2L), any(), any())).thenReturn(false);

        service.isInteractionBlocked(1L, 2L, UserBlockScope.ALL);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UserBlockScope>> scopes = ArgumentCaptor.forClass(Collection.class);
        verify(blockRepository).existsEffectiveBetween(eq(1L), eq(2L), scopes.capture(), any());
        assertEquals(List.of(UserBlockScope.ALL), List.copyOf(scopes.getValue()));
    }

    @Test
    void assertInteractionAllowed_shouldThrowWhenBlocked() {
        when(blockRepository.existsEffectiveBetween(eq(1L), eq(2L), any(), any())).thenReturn(true);

        assertThrows(ForbiddenException.class,
                () -> service.assertInteractionAllowed(1L, 2L, UserBlockScope.PROFILE, "blocked"));
    }

    private UserBlockRelation relation(Long id,
                                       User blocker,
                                       User blocked,
                                       UserBlockScope scope,
                                       LocalDateTime createdAt,
                                       LocalDateTime expiresAt) {
        return UserBlockRelation.builder()
                .id(id)
                .blocker(blocker)
                .blocked(blocked)
                .scope(scope)
                .createdAt(createdAt)
                .expiresAt(expiresAt)
                .build();
    }

    private User user(Long id, String name) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setDeleted(false);
        return user;
    }
}
