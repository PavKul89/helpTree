package org.example.helptreeservice.controller;

import org.example.helptreeservice.dto.helps.CancelHelpRequest;
import org.example.helptreeservice.dto.helps.HelpRequest;
import org.example.helptreeservice.dto.helps.HelpResponse;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.HelpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HelpControllerTest {

    @Mock private HelpService helpService;
    @Mock private AuthorizationService authService;

    private HelpController controller;

    @BeforeEach
    void setUp() {
        controller = new HelpController(helpService, authService);
    }

    @Test
    void completeHelp_shouldPassActorAndAdminFlagToService() {
        when(authService.getCurrentUser())
                .thenReturn(new AuthorizationService.UserContext(7L, "USER", "u@test.local"));
        when(authService.isAdmin()).thenReturn(false);
        when(helpService.completeHelp(3L, 7L, false))
                .thenReturn(HelpResponse.builder().id(3L).build());

        ResponseEntity<HelpResponse> response = controller.completeHelp(3L);

        verify(helpService).completeHelp(3L, 7L, false);
        verify(helpService, never()).confirmHelp(anyLong(), anyLong(), anyBoolean());
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void confirmHelp_shouldPassActorAndAdminFlagToService() {
        when(authService.getCurrentUser())
                .thenReturn(new AuthorizationService.UserContext(7L, "ADMIN", "a@test.local"));
        when(authService.isAdmin()).thenReturn(true);
        when(helpService.confirmHelp(3L, 7L, true))
                .thenReturn(HelpResponse.builder().id(3L).build());

        controller.confirmHelp(3L);

        verify(helpService).confirmHelp(3L, 7L, true);
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

    private HelpRequest request() {
        HelpRequest request = new HelpRequest();
        request.setPostId(1L);
        return request;
    }
}
