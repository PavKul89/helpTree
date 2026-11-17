package org.example.helptreeservice.controller;

import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalUserControllerTest {

    @Mock private AuthorizationService authService;
    @Mock private UserService userService;

    @InjectMocks private InternalUserController controller;

    @BeforeEach
    void denyByDefault() {
        when(authService.isAdmin()).thenReturn(false);
    }

    @Test
    void updateRating_shouldRejectNonAdmin() {
        assertThrows(ForbiddenException.class,
                () -> controller.updateUserRating(7L, 4.5));

        verifyNoInteractions(userService);
    }

    @Test
    void updateRating_shouldDelegateToServiceForAdmin() {
        when(authService.isAdmin()).thenReturn(true);

        assertEquals(200, controller.updateUserRating(7L, 4.5).getStatusCode().value());
        verify(userService).updateUserRating(7L, 4.5);
    }
}
