package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.activity.ActivityDto;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.NotFoundException;
import org.example.helptreeservice.repository.ActivityRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    @Mock private ActivityRepository activityRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuthorizationService authorizationService;

    private ActivityService activityService;

    @BeforeEach
    void setUp() {
        activityService = new ActivityService(activityRepository, userRepository, authorizationService);
        lenient().when(userRepository.existsByIdAndDeletedFalse(1L)).thenReturn(true);
    }

    @Test
    void publicViewerDoesNotReceiveParticipantIdentity() {
        when(authorizationService.canManageUser(1L)).thenReturn(false);
        when(activityRepository.findUserActivity(eq(1L), eq(false), eq("ALL"), any(Pageable.class)))
                .thenReturn(page(row("HELP_GIVEN", LocalDateTime.now())));

        PageResponse<ActivityDto> response =
                activityService.getUserActivities(1L, "ALL", 0, 20);

        assertEquals(1, response.getContent().size());
        ActivityDto activity = response.getContent().get(0);
        assertEquals("HELP_GIVEN", activity.getType());
        assertNull(activity.getRelatedUserId());
        assertNull(activity.getRelatedUserName());
        assertEquals(10L, activity.getRelatedPostId());
        assertEquals("CONFIRMED", activity.getStatus());
        assertEquals(1, response.getTotalElements());
    }

    @Test
    void ownerReceivesParticipantIdentity() {
        when(authorizationService.canManageUser(1L)).thenReturn(true);
        when(activityRepository.findUserActivity(eq(1L), eq(true), eq("ALL"), any(Pageable.class)))
                .thenReturn(page(row("HELP_GIVEN", LocalDateTime.now())));

        PageResponse<ActivityDto> response =
                activityService.getUserActivities(1L, null, 0, 20);

        ActivityDto activity = response.getContent().get(0);
        assertEquals(2L, activity.getRelatedUserId());
        assertEquals("Receiver", activity.getRelatedUserName());
    }

    @Test
    void achievementRowsAreMappedFromTheTypeCode() {
        when(authorizationService.canManageUser(1L)).thenReturn(true);
        when(activityRepository.findUserActivity(eq(1L), eq(true), eq("ACHIEVEMENT_EARNED"), any(Pageable.class)))
                .thenReturn(page(new Object[]{
                        "ACHIEVEMENT_EARNED", 5L, LocalDateTime.now(),
                        null, null, null, null, null, null, "FIRST_HELP"
                }));

        PageResponse<ActivityDto> response =
                activityService.getUserActivities(1L, "achievement_earned", 0, 20);

        ActivityDto activity = response.getContent().get(0);
        assertTrue(activity.getTitle().contains("Первая помощь"));
        assertEquals("COMMON", activity.getCategory());
    }

    @Test
    void unknownTypeIsRejectedBeforeHittingTheDatabase() {
        assertThrows(BadRequestException.class,
                () -> activityService.getUserActivities(1L, "NOT_A_TYPE", 0, 20));
        verifyNoInteractions(activityRepository);
    }

    @Test
    void deletedUserIsNotFound() {
        when(userRepository.existsByIdAndDeletedFalse(1L)).thenReturn(false);

        assertThrows(NotFoundException.class,
                () -> activityService.getUserActivities(1L, "ALL", 0, 20));
        verifyNoInteractions(activityRepository);
    }

    private PageImpl<Object[]> page(Object[] row) {
        return new PageImpl<Object[]>(List.<Object[]>of(row), PageRequest.of(0, 20), 1);
    }

    private Object[] row(String type, LocalDateTime time) {
        return new Object[]{
                type, 5L, time, 2L, "Receiver", 10L, "Post title",
                "REPAIR", "CONFIRMED", "Post title"
        };
    }
}
