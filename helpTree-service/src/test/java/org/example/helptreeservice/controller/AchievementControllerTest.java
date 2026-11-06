package org.example.helptreeservice.controller;

import org.example.helptreeservice.dto.achievement.AchievementDto;
import org.example.helptreeservice.service.AchievementService;
import org.example.helptreeservice.service.PrivacyPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AchievementControllerTest {

    @Mock private AchievementService achievementService;
    @Mock private PrivacyPolicy privacyPolicy;

    private AchievementController controller;

    @BeforeEach
    void setUp() {
        controller = new AchievementController(achievementService, privacyPolicy);
    }

    @Test
    void catalogueWithoutUserReturnsDefinitionsOnly() {
        when(achievementService.getAllAchievementTypes()).thenReturn(List.of());

        controller.getAllAchievements(null);

        verify(achievementService).getAllAchievementTypes();
        verify(achievementService, never()).getAllAchievementTypesWithProgress(7L);
    }

    @Test
    void ownerGetsThePrivateProgressProjection() {
        when(privacyPolicy.canViewPrivateActivity(7L)).thenReturn(true);
        List<AchievementDto> progress = List.of(
                AchievementDto.builder().type("FIRST_HELP").currentProgress(1).build());
        when(achievementService.getAllAchievementTypesWithProgress(7L)).thenReturn(progress);

        var response = controller.getAllAchievements(7L);

        assertEquals(progress, response.getBody());
        verify(achievementService, never()).getUserAchievements(7L);
    }

    @Test
    void publicViewerGetsOnlyEarnedAchievements() {
        when(privacyPolicy.canViewPrivateActivity(7L)).thenReturn(false);
        List<AchievementDto> earned = List.of(
                AchievementDto.builder().type("FIRST_HELP").isEarned(true).build());
        when(achievementService.getUserAchievements(7L)).thenReturn(earned);

        var response = controller.getAllAchievements(7L);

        assertEquals(earned, response.getBody());
        verify(achievementService, never()).getAllAchievementTypesWithProgress(7L);
    }
}
