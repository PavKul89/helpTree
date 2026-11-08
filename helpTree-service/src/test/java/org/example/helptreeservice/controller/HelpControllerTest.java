package org.example.helptreeservice.controller;

import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.helps.CancelHelpRequest;
import org.example.helptreeservice.dto.helps.HelpRequest;
import org.example.helptreeservice.dto.helps.HelpResponse;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.HelpService;
import org.example.helptreeservice.service.PrivacyPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HelpControllerTest {

    @Mock private HelpService helpService;
    @Mock private AuthorizationService authService;
    @Mock private PrivacyPolicy privacyPolicy;
    @Mock private PaginationConfig paginationConfig;

    private HelpController controller;

    @BeforeEach
    void setUp() {
        controller = new HelpController(helpService, authService, privacyPolicy, paginationConfig);
    }

    @Test
    void completeHelp_shouldPassActorAndAdminFlagToService() {
        when(authService.getCurrentUser())
                .thenReturn(new AuthorizationService.UserContext(7L, "USER", "u@test.local"));
        when(authService.isAdmin()).thenReturn(false);
        when(helpService.completeHelp(3L, 7L, false))
                .thenReturn(HelpResponse.builder().id(3L).build());

        ResponseEntity<HelpResponse> response = controller.completeHelp(3L, null);

        verify(helpService).completeHelp(3L, 7L, false);
        verify(helpService, never()).confirmHelp(anyLong(), anyLong(), anyBoolean());
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void confirmHelp_shouldPassActorAndAdminFlagToService() {
        when(authService.getCurrentUser())
                .thenReturn(new AuthorizationService.UserContext(7L, "ADMIN", "a@test.local"));
        when(authService.isAdmin()).thenReturn(true);
        when(helpService.confirmHelp(3L, 7L, true, null))
                .thenReturn(HelpResponse.builder().id(3L).build());

        controller.confirmHelp(3L, null);

        verify(helpService).confirmHelp(3L, 7L, true, null);
    }

    @Test
    void cancelHelp_shouldForwardReason() {
        when(authService.getCurrentUser())
                .thenReturn(new AuthorizationService.UserContext(7L, "USER", "u@test.local"));
        when(authService.isAdmin()).thenReturn(false);
        when(helpService.cancelHelp(3L, 7L, false, "Не смогу"))
                .thenReturn(HelpResponse.builder().id(3L).build());
        CancelHelpRequest request = new CancelHelpRequest();
        request.setReason("Не смогу");

        controller.cancelHelp(3L, request);

        verify(helpService).cancelHelp(3L, 7L, false, "Не смогу");
    }

    @Test
    void cancelHelp_shouldAllowMissingBodyForBackwardCompatibility() {
        when(authService.getCurrentUser())
                .thenReturn(new AuthorizationService.UserContext(7L, "USER", "u@test.local"));
        when(authService.isAdmin()).thenReturn(false);
        when(helpService.cancelHelp(3L, 7L, false, null))
                .thenReturn(HelpResponse.builder().id(3L).build());

        controller.cancelHelp(3L, null);

        verify(helpService).cancelHelp(3L, 7L, false, null);
    }

    @Test
    void acceptHelp_shouldRejectUnauthenticatedRequest() {
        when(authService.getCurrentUser()).thenReturn(null);

        assertThrows(ForbiddenException.class,
                () -> controller.acceptHelp(request()));
        verifyNoInteractions(helpService);
    }

    @Test
    void history_shouldRequireAuthentication() {
        when(authService.getCurrentUser()).thenReturn(null);

        assertThrows(ForbiddenException.class, () -> controller.getHelpHistory(3L));
        verifyNoInteractions(helpService);
    }

    @Test
    void getHelpsByHelper_shouldReturnTheSharedPageEnvelope() {
        when(privacyPolicy.canViewHelp(5L, null)).thenReturn(true);
        when(paginationConfig.getMaxPageSize()).thenReturn(100);
        Page<HelpResponse> page = new PageImpl<>(
                List.of(HelpResponse.builder().id(1L).build()),
                PageRequest.of(0, 1),
                3);
        when(helpService.getHelpsByHelper(eq(5L), any(Pageable.class))).thenReturn(page);

        var response = controller.getHelpsByHelper(5L, 0, 1);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(3, response.getBody().getTotalElements());
        assertEquals(1, response.getBody().getContent().size());
        assertEquals(3, response.getBody().getTotalPages());
        assertTrue(response.getBody().isHasNext());
        assertEquals(1, response.getBody().getSize());
        assertEquals(0, response.getBody().getNumber());
    }

    private HelpRequest request() {
        HelpRequest request = new HelpRequest();
        request.setPostId(1L);
        return request;
    }
}
