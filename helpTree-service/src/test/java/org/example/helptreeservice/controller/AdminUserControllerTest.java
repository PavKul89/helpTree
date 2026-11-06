package org.example.helptreeservice.controller;

import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.admin.AdminAnonymizeRequest;
import org.example.helptreeservice.dto.admin.AdminAuditLogDto;
import org.example.helptreeservice.dto.admin.AdminCreateUserRequest;
import org.example.helptreeservice.dto.admin.AdminInviteUserRequest;
import org.example.helptreeservice.dto.admin.AdminReasonRequest;
import org.example.helptreeservice.dto.admin.AdminRoleChangeRequest;
import org.example.helptreeservice.dto.users.UserDto;
import org.example.helptreeservice.enums.Role;
import org.example.helptreeservice.enums.UserStatus;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.exception.UnauthorizedException;
import org.example.helptreeservice.service.AdminUserService;
import org.example.helptreeservice.service.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserControllerTest {

    @Mock private AdminUserService adminUserService;
    @Mock private AuthorizationService authService;
    @Mock private PaginationConfig paginationConfig;

    @InjectMocks private AdminUserController controller;

    @BeforeEach
    void denyByDefault() {
        lenient().when(authService.isAdmin()).thenReturn(false);
        lenient().when(paginationConfig.getDefaultPageSize()).thenReturn(20);
        lenient().when(paginationConfig.getMaxPageSize()).thenReturn(100);
    }

    private void asAdmin(Long adminId) {
        when(authService.isAdmin()).thenReturn(true);
        when(authService.getCurrentUser()).thenReturn(
                new AuthorizationService.UserContext(adminId, "ADMIN", "admin@test.com"));
    }

    private UserDto userDto(Long id) {
        return UserDto.builder().id(id).name("User " + id).role(Role.USER).build();
    }

    @Test
    void searchShouldForbidNonAdmin() {
        assertThrows(ForbiddenException.class,
                () -> controller.search(0, 20, "q", null, null, null, null, "createdAt", "desc"));
        verifyNoInteractions(adminUserService);
    }

    @Test
    void getShouldForbidNonAdmin() {
        assertThrows(ForbiddenException.class, () -> controller.get(1L));
        verifyNoInteractions(adminUserService);
    }

    @Test
    void createShouldForbidNonAdmin() {
        assertThrows(ForbiddenException.class,
                () -> controller.create(new AdminCreateUserRequest()));
        verifyNoInteractions(adminUserService);
    }

    @Test
    void searchShouldDelegateFiltersAndClampPageSize() {
        asAdmin(9L);
        when(adminUserService.search(any(Pageable.class), eq("ivan"), eq(Role.ADMIN),
                eq(UserStatus.NEWBIE), eq(true), eq(true)))
                .thenReturn(new PageImpl<>(List.of(userDto(1L))));

        var response = controller.search(0, 5000, "ivan", Role.ADMIN,
                UserStatus.NEWBIE, true, true, "rating", "asc");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().getContent().size());
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(adminUserService).search(captor.capture(), eq("ivan"), eq(Role.ADMIN),
                eq(UserStatus.NEWBIE), eq(true), eq(true));
        assertEquals(100, captor.getValue().getPageSize());
        assertEquals("rating", captor.getValue().getSort().getOrderFor("rating").getProperty());
        assertEquals("id", captor.getValue().getSort().getOrderFor("id").getProperty());
    }

    @Test
    void searchShouldIgnoreUnknownSortProperty() {
        asAdmin(9L);
        when(adminUserService.search(any(Pageable.class), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        controller.search(0, 20, null, null, null, null, null, "password", "desc");

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(adminUserService).search(captor.capture(), any(), any(), any(), any(), any());
        assertEquals("id", captor.getValue().getSort().getOrderFor("id").getProperty());
    }

    @Test
    void deletedViewShouldAlwaysRequestDeletedAccounts() {
        asAdmin(9L);
        when(adminUserService.search(any(Pageable.class), any(), any(), any(), any(), eq(true)))
                .thenReturn(Page.empty());

        var response = controller.getDeleted(0, 20, null, null, null, "deletedAt", "desc");

        assertEquals(200, response.getStatusCode().value());
        verify(adminUserService).search(any(Pageable.class), isNull(), isNull(), isNull(),
                isNull(), eq(true));
    }

    @Test
    void createShouldPassAdminIdAndReturnCreated() {
        asAdmin(9L);
        AdminCreateUserRequest request = new AdminCreateUserRequest();
        when(adminUserService.createWithPassword(request, 9L)).thenReturn(userDto(5L));

        var response = controller.create(request);

        assertEquals(201, response.getStatusCode().value());
        assertEquals(5L, response.getBody().getId());
    }

    @Test
    void inviteShouldPassAdminId() {
        asAdmin(9L);
        AdminInviteUserRequest request = new AdminInviteUserRequest();
        when(adminUserService.invite(request, 9L)).thenReturn(userDto(6L));

        var response = controller.invite(request);

        assertEquals(201, response.getStatusCode().value());
        assertEquals(6L, response.getBody().getId());
    }

    @Test
    void changeRoleShouldPassReasonAndAdminId() {
        asAdmin(9L);
        AdminRoleChangeRequest request = new AdminRoleChangeRequest();
        request.setRole(Role.ADMIN);
        request.setReason("promote");
        when(adminUserService.changeRole(4L, Role.ADMIN, "promote", 9L))
                .thenReturn(userDto(4L));

        var response = controller.changeRole(4L, request);

        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void deleteShouldRequireReasonAndAdminId() {
        asAdmin(9L);

        var response = controller.delete(4L, "spam");

        assertEquals(204, response.getStatusCode().value());
        verify(adminUserService).delete(4L, "spam", 9L);
    }

    @Test
    void restoreShouldPassReason() {
        asAdmin(9L);
        AdminReasonRequest request = new AdminReasonRequest();
        request.setReason("appeal accepted");

        var response = controller.restore(4L, request);

        assertEquals(204, response.getStatusCode().value());
        verify(adminUserService).restore(4L, "appeal accepted", 9L);
    }

    @Test
    void anonymizeShouldPassConfirmEmail() {
        asAdmin(9L);
        AdminAnonymizeRequest request = new AdminAnonymizeRequest();
        request.setReason("GDPR");
        request.setConfirmEmail("user@test.com");

        var response = controller.anonymize(4L, request);

        assertEquals(204, response.getStatusCode().value());
        verify(adminUserService).anonymize(4L, "GDPR", "user@test.com", 9L);
    }

    @Test
    void exportShouldRequireReason() {
        asAdmin(9L);
        when(adminUserService.export(4L, "request", 9L)).thenReturn(Map.of("user", Map.of()));

        var response = controller.export(4L, "request");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().containsKey("user"));
    }

    @Test
    void auditHistoryShouldMapPage() {
        asAdmin(9L);
        AdminAuditLogDto entry = AdminAuditLogDto.builder()
                .id(1L).adminId(9L).action("USER_ROLE_CHANGED").entityType("USER")
                .entityId(4L).reason("promote").build();
        when(adminUserService.auditHistory(eq(4L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        var response = controller.auditHistory(4L, 0, 20);

        assertEquals(1, response.getBody().getContent().size());
        assertEquals("USER_ROLE_CHANGED", response.getBody().getContent().get(0).getAction());
    }

    @Test
    void adminFlagWithoutUserContextShouldBeUnauthorized() {
        when(authService.isAdmin()).thenReturn(true);
        when(authService.getCurrentUser()).thenReturn(null);

        assertThrows(UnauthorizedException.class, () -> controller.get(1L));
        verifyNoInteractions(adminUserService);
    }
}
