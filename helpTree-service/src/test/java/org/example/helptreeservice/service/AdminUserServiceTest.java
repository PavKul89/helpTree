package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.admin.AdminAuditLogDto;
import org.example.helptreeservice.dto.admin.AdminCreateUserRequest;
import org.example.helptreeservice.dto.admin.AdminInviteUserRequest;
import org.example.helptreeservice.dto.users.CreateUserRequest;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.AdminAuditAction;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.UserStatus;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ConflictException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.mapper.UserMapper;
import org.example.helptreeservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserService userService;
    @Mock private UserMapper userMapper;
    @Mock private AccountSecurityService accountSecurityService;
    @Mock private AdminAuditService adminAuditService;
    @Mock private UserDataExportService userDataExportService;

    @InjectMocks private AdminUserService adminUserService;

    private static final Long ADMIN_ID = 9L;

    private UserDto userDto(Long id, Role role, String email) {
        return UserDto.builder()
                .id(id).email(email).role(role).status(UserStatus.NEWBIE).deleted(false)
                .build();
    }

    private User entity(Long id, Role role, boolean deleted, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        user.setDeleted(deleted);
        return user;
    }

    // ---------------------------------------------------------------
    // Search / read
    // ---------------------------------------------------------------

    @Test
    void searchShouldDelegateTheDeletionScope() {
        Page<UserDto> page = new PageImpl<>(List.of(userDto(1L, Role.USER, "u@test.com")));
        when(userService.getAllUsers(any(Pageable.class), eq("q"), eq(Role.USER),
                eq(UserStatus.NEWBIE), eq(false), eq(true))).thenReturn(page);

        Page<UserDto> result = adminUserService.search(Pageable.unpaged(), "q",
                Role.USER, UserStatus.NEWBIE, false, true);

        assertSame(page, result);
    }

    @Test
    void getShouldReadDeletedRowsToo() {
        User user = entity(1L, Role.USER, true, "u@test.com");
        when(userService.getUserEntityByIdIncludingDeleted(1L)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(userDto(1L, Role.USER, "u@test.com"));

        UserDto result = adminUserService.get(1L);

        assertEquals(1L, result.getId());
    }

    // ---------------------------------------------------------------
    // Creation
    // ---------------------------------------------------------------

    @Test
    void createShouldRejectMissingReasonBeforePersisting() {
        AdminCreateUserRequest request = new AdminCreateUserRequest();
        request.setReason("  ");

        assertThrows(BadRequestException.class,
                () -> adminUserService.createWithPassword(request, ADMIN_ID));

        verifyNoInteractions(userService, adminAuditService);
    }

    @Test
    void createShouldDefaultToUserRoleAndAudit() {
        AdminCreateUserRequest request = new AdminCreateUserRequest();
        request.setName("Ivan");
        request.setEmail("Ivan@Test.com");
        request.setPassword("Strong1A");
        request.setPhone("+79990000000");
        request.setCity("Moscow");
        request.setReason("manual onboarding");

        UserDto created = userDto(7L, Role.USER, "ivan@test.com");
        when(userService.createUserWithRole(any(CreateUserRequest.class), eq(Role.USER)))
                .thenReturn(created);

        UserDto result = adminUserService.createWithPassword(request, ADMIN_ID);

        assertEquals(7L, result.getId());
        ArgumentCaptor<CreateUserRequest> captor = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(userService).createUserWithRole(captor.capture(), eq(Role.USER));
        assertEquals("Ivan@Test.com", captor.getValue().getEmail());
        verify(adminAuditService).record(eq(ADMIN_ID), eq(AdminAuditAction.USER_CREATED),
                eq(AdminAuditService.ENTITY_USER), eq(7L), isNull(), anyMap(),
                eq("manual onboarding"));
    }

    @Test
    void createShouldHonourExplicitAdminRole() {
        AdminCreateUserRequest request = new AdminCreateUserRequest();
        request.setName("Admin");
        request.setEmail("admin@test.com");
        request.setPassword("Strong1A");
        request.setPhone("+79990000000");
        request.setCity("Moscow");
        request.setRole(Role.ADMIN);
        request.setReason("bootstrap");

        when(userService.createUserWithRole(any(CreateUserRequest.class), eq(Role.ADMIN)))
                .thenReturn(userDto(8L, Role.ADMIN, "admin@test.com"));

        adminUserService.createWithPassword(request, ADMIN_ID);

        verify(userService).createUserWithRole(any(CreateUserRequest.class), eq(Role.ADMIN));
    }

    @Test
    void inviteShouldRejectDuplicateEmail() {
        AdminInviteUserRequest request = new AdminInviteUserRequest();
        request.setEmail("  User@Test.com ");
        request.setReason("invite");
        when(userRepository.existsByEmailIgnoreCase("user@test.com")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> adminUserService.invite(request, ADMIN_ID));

        verify(userService, never()).createUserWithRole(any(), any());
        verifyNoInteractions(accountSecurityService, adminAuditService);
    }

    @Test
    void inviteShouldCreateLockedAccountAndSendInvitation() {
        AdminInviteUserRequest request = new AdminInviteUserRequest();
        request.setName("Invited");
        request.setEmail(" Invited@Test.com ");
        request.setPhone("+79990000000");
        request.setCity("Moscow");
        request.setReason("partner account");

        when(userRepository.existsByEmailIgnoreCase("invited@test.com")).thenReturn(false);
        when(userService.createUserWithRole(any(CreateUserRequest.class), eq(Role.USER)))
                .thenReturn(userDto(11L, Role.USER, "invited@test.com"));
        User entity = entity(11L, Role.USER, false, "invited@test.com");
        when(userRepository.findById(11L)).thenReturn(Optional.of(entity));

        UserDto result = adminUserService.invite(request, ADMIN_ID);

        assertEquals(11L, result.getId());
        ArgumentCaptor<CreateUserRequest> captor = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(userService).createUserWithRole(captor.capture(), eq(Role.USER));
        assertEquals("invited@test.com", captor.getValue().getEmail());
        assertNotNull(captor.getValue().getPassword());
        assertTrue(captor.getValue().getPassword().length() >= 8);
        verify(accountSecurityService).sendAccountInvitation(entity);
        verify(adminAuditService).record(eq(ADMIN_ID), eq(AdminAuditAction.USER_INVITED),
                eq(AdminAuditService.ENTITY_USER), eq(11L), isNull(), anyMap(),
                eq("partner account"));
    }

    @Test
    void inviteShouldNotAuditWhenMailCannotBeSent() {
        AdminInviteUserRequest request = new AdminInviteUserRequest();
        request.setName("Invited");
        request.setEmail("invited@test.com");
        request.setPhone("+79990000000");
        request.setCity("Moscow");
        request.setReason("partner account");

        when(userRepository.existsByEmailIgnoreCase("invited@test.com")).thenReturn(false);
        when(userService.createUserWithRole(any(CreateUserRequest.class), any()))
                .thenReturn(userDto(11L, Role.USER, "invited@test.com"));
        User entity = entity(11L, Role.USER, false, "invited@test.com");
        when(userRepository.findById(11L)).thenReturn(Optional.of(entity));
        doThrow(new BadRequestException("mail disabled"))
                .when(accountSecurityService).sendAccountInvitation(entity);

        assertThrows(BadRequestException.class,
                () -> adminUserService.invite(request, ADMIN_ID));

        verifyNoInteractions(adminAuditService);
    }

    // ---------------------------------------------------------------
    // Role change
    // ---------------------------------------------------------------

    @Test
    void changeRoleShouldRequireReason() {
        assertThrows(BadRequestException.class,
                () -> adminUserService.changeRole(4L, Role.ADMIN, " ", ADMIN_ID));

        verifyNoInteractions(userService, adminAuditService);
    }

    @Test
    void changeRoleShouldRejectDeletedUser() {
        when(userRepository.findById(4L)).thenReturn(Optional.of(entity(4L, Role.USER, true, "u@test.com")));

        assertThrows(NotFoundException.class,
                () -> adminUserService.changeRole(4L, Role.ADMIN, "promote", ADMIN_ID));

        verify(userService, never()).changeRole(anyLong(), any());
    }

    @Test
    void changeRoleShouldAuditBeforeAndAfter() {
        when(userRepository.findById(4L)).thenReturn(Optional.of(entity(4L, Role.USER, false, "u@test.com")));
        when(userService.changeRole(4L, Role.ADMIN)).thenReturn(userDto(4L, Role.ADMIN, "u@test.com"));

        adminUserService.changeRole(4L, Role.ADMIN, "promote", ADMIN_ID);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> before = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> after = ArgumentCaptor.forClass(Map.class);
        verify(adminAuditService).record(eq(ADMIN_ID), eq(AdminAuditAction.USER_ROLE_CHANGED),
                eq(AdminAuditService.ENTITY_USER), eq(4L), before.capture(), after.capture(),
                eq("promote"));
        assertEquals("USER", before.getValue().get("role"));
        assertEquals("ADMIN", after.getValue().get("role"));
    }

    @Test
    void changeRoleShouldNotAuditWhenRoleIsUnchanged() {
        when(userRepository.findById(4L)).thenReturn(Optional.of(entity(4L, Role.USER, false, "u@test.com")));
        when(userService.changeRole(4L, Role.USER)).thenReturn(userDto(4L, Role.USER, "u@test.com"));

        adminUserService.changeRole(4L, Role.USER, "no-op", ADMIN_ID);

        verifyNoInteractions(adminAuditService);
    }

    // ---------------------------------------------------------------
    // Delete / restore
    // ---------------------------------------------------------------

    @Test
    void deleteShouldRequireReason() {
        assertThrows(BadRequestException.class,
                () -> adminUserService.delete(4L, null, ADMIN_ID));
        verifyNoInteractions(userService, adminAuditService);
    }

    @Test
    void deleteShouldAudit() {
        when(userRepository.findById(4L)).thenReturn(Optional.of(entity(4L, Role.USER, false, "u@test.com")));

        adminUserService.delete(4L, "spam", ADMIN_ID);

        verify(userService).deleteUser(4L);
        verify(adminAuditService).record(eq(ADMIN_ID), eq(AdminAuditAction.USER_DELETED),
                eq(AdminAuditService.ENTITY_USER), eq(4L), anyMap(), anyMap(), eq("spam"));
    }

    @Test
    void restoreShouldRequireReason() {
        assertThrows(BadRequestException.class,
                () -> adminUserService.restore(4L, "  ", ADMIN_ID));
        verifyNoInteractions(userService, adminAuditService);
    }

    @Test
    void restoreShouldAudit() {
        adminUserService.restore(4L, "appeal accepted", ADMIN_ID);

        verify(userService).restoreUser(4L);
        verify(adminAuditService).record(eq(ADMIN_ID), eq(AdminAuditAction.USER_RESTORED),
                eq(AdminAuditService.ENTITY_USER), eq(4L), anyMap(), anyMap(),
                eq("appeal accepted"));
    }

    // ---------------------------------------------------------------
    // Anonymization / export
    // ---------------------------------------------------------------

    @Test
    void anonymizeShouldRequireReason() {
        assertThrows(BadRequestException.class,
                () -> adminUserService.anonymize(4L, "", null, ADMIN_ID));
        verifyNoInteractions(userService, adminAuditService);
    }

    @Test
    void anonymizeShouldRejectMismatchedConfirmationEmailBeforeMutation() {
        when(userRepository.findById(4L)).thenReturn(Optional.of(entity(4L, Role.USER, false, "u@test.com")));

        assertThrows(BadRequestException.class,
                () -> adminUserService.anonymize(4L, "GDPR", "other@test.com", ADMIN_ID));

        verify(userService, never()).anonymizeUser(anyLong(), anyString());
        verifyNoInteractions(adminAuditService);
    }

    @Test
    void anonymizeShouldDelegateAndAudit() {
        when(userRepository.findById(4L)).thenReturn(Optional.of(entity(4L, Role.USER, false, "u@test.com")));

        adminUserService.anonymize(4L, "GDPR", "u@test.com", ADMIN_ID);

        verify(userService).anonymizeUser(4L, "GDPR");
        verify(adminAuditService).record(eq(ADMIN_ID), eq(AdminAuditAction.USER_ANONYMIZED),
                eq(AdminAuditService.ENTITY_USER), eq(4L), anyMap(), anyMap(), eq("GDPR"));
    }

    @Test
    void exportShouldRequireReason() {
        assertThrows(BadRequestException.class,
                () -> adminUserService.export(4L, " ", ADMIN_ID));
        verifyNoInteractions(userService, adminAuditService);
    }

    @Test
    void exportShouldReturnDocumentAndAudit() {
        User user = entity(4L, Role.USER, false, "u@test.com");
        Map<String, Object> document = Map.of("user", Map.of("id", 4L));
        when(userService.getUserEntityByIdIncludingDeleted(4L)).thenReturn(user);
        when(userDataExportService.export(user)).thenReturn(document);

        Map<String, Object> result = adminUserService.export(4L, "user request", ADMIN_ID);

        assertEquals(document, result);
        verify(adminAuditService).record(eq(ADMIN_ID), eq(AdminAuditAction.USER_DATA_EXPORTED),
                eq(AdminAuditService.ENTITY_USER), eq(4L), isNull(), anyMap(),
                eq("user request"));
    }

    @Test
    void auditHistoryShouldFailForUnknownUser() {
        when(userService.getUserEntityByIdIncludingDeleted(4L))
                .thenThrow(new NotFoundException("not found"));

        assertThrows(NotFoundException.class,
                () -> adminUserService.auditHistory(4L, Pageable.unpaged()));

        verifyNoInteractions(adminAuditService);
    }

    @Test
    void auditHistoryShouldDelegateAfterExistenceCheck() {
        when(userService.getUserEntityByIdIncludingDeleted(4L))
                .thenReturn(entity(4L, Role.USER, true, "u@test.com"));
        Page<AdminAuditLogDto> page = Page.empty();
        when(adminAuditService.history(AdminAuditService.ENTITY_USER, 4L, Pageable.unpaged()))
                .thenReturn(page);

        assertSame(page, adminUserService.auditHistory(4L, Pageable.unpaged()));
    }
}
