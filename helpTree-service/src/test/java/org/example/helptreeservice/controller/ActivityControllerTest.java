package org.example.helptreeservice.controller;

import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.activity.ActivityDto;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.service.ActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityControllerTest {

    @Mock private ActivityService activityService;

    private ActivityController controller;

    @BeforeEach
    void setUp() {
        PaginationConfig config = new PaginationConfig();
        config.setDefaultPageSize(20);
        config.setMaxPageSize(100);
        controller = new ActivityController(activityService, config);
    }

    @Test
    void sizeIsClampedToTheConfiguredMaximum() {
        when(activityService.getUserActivities(eq(1L), eq("ALL"), eq(0), eq(100)))
                .thenReturn(PageResponse.empty(0, 100));

        controller.getUserActivities(1L, 0, 500, null, "ALL");

        verify(activityService).getUserActivities(1L, "ALL", 0, 100);
    }

    @Test
    void legacyLimitIsAcceptedAsSizeAlias() {
        when(activityService.getUserActivities(eq(1L), eq("ALL"), eq(0), eq(10)))
                .thenReturn(PageResponse.empty(0, 10));

        controller.getUserActivities(1L, null, null, 10, "ALL");

        verify(activityService).getUserActivities(1L, "ALL", 0, 10);
    }

    @Test
    void missingUserIdReturnsAnEmptyPageWithoutQuerying() {
        ResponseEntity<PageResponse<ActivityDto>> response =
                controller.getUserActivities(null, 0, 20, null, "ALL");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isEmpty());
        verifyNoInteractions(activityService);
    }

    @Test
    void negativePageFallsBackToFirstPage() {
        when(activityService.getUserActivities(eq(1L), eq("ALL"), eq(0), eq(20)))
                .thenReturn(PageResponse.empty(0, 20));

        controller.getUserActivities(1L, -5, null, null, "ALL");

        verify(activityService).getUserActivities(1L, "ALL", 0, 20);
    }
}
