package org.example.helptreeservice.service;

import org.example.helptreeservice.enums.ActivityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivacyPolicyTest {

    @Mock private AuthorizationService authorizationService;

    private PrivacyPolicy privacyPolicy;

    @BeforeEach
    void setUp() {
        privacyPolicy = new PrivacyPolicy(authorizationService);
    }

    @Test
    void anonymousViewerSeesOnlyThePublicProjection() {
        when(authorizationService.getCurrentUser()).thenReturn(null);

        assertEquals(PrivacyPolicy.Audience.PUBLIC, privacyPolicy.audienceFor(7L));
        assertFalse(privacyPolicy.canViewFullPost(7L));
        assertFalse(privacyPolicy.canViewExactCoordinates(7L));
        assertFalse(privacyPolicy.canViewBoostExpiry(7L));
        assertFalse(privacyPolicy.canViewPrivateActivity(7L));
        assertFalse(privacyPolicy.canViewPrivateGraph(7L));
        assertFalse(privacyPolicy.canViewRatingHistory(7L));
        assertFalse(privacyPolicy.canViewAllPostStatuses(null));
    }

    @Test
    void ownerSeesEverythingAboutTheirOwnData() {
        when(authorizationService.getCurrentUser())
                .thenReturn(new AuthorizationService.UserContext(7L, "USER", "u@test.local"));

        assertEquals(PrivacyPolicy.Audience.OWNER, privacyPolicy.audienceFor(7L));
        assertTrue(privacyPolicy.canViewFullPost(7L));
        assertTrue(privacyPolicy.canViewPrivateActivity(7L));
        assertTrue(privacyPolicy.canViewPrivateGraph(7L));
        assertTrue(privacyPolicy.canViewRatingHistory(7L));
        assertTrue(privacyPolicy.canViewAllPostStatuses(7L));
    }

    @Test
    void anotherUserNeverGetsOwnerFields() {
        when(authorizationService.getCurrentUser())
                .thenReturn(new AuthorizationService.UserContext(8L, "USER", "other@test.local"));

        assertEquals(PrivacyPolicy.Audience.PUBLIC, privacyPolicy.audienceFor(7L));
        assertFalse(privacyPolicy.canViewFullPost(7L));
        assertFalse(privacyPolicy.canViewPrivateActivity(7L));
        assertFalse(privacyPolicy.canViewPrivateGraph(7L));
        assertFalse(privacyPolicy.canViewRatingHistory(7L));
    }

    @Test
    void adminHasModerationAccessWithoutASeparateRole() {
        when(authorizationService.getCurrentUser())
                .thenReturn(new AuthorizationService.UserContext(99L, "ADMIN", "a@test.local"));

        assertEquals(PrivacyPolicy.Audience.ADMIN, privacyPolicy.audienceFor(7L));
        assertTrue(privacyPolicy.canViewFullPost(7L));
        assertTrue(privacyPolicy.canViewPrivateActivity(7L));
        assertTrue(privacyPolicy.canViewPrivateGraph(7L));
        assertTrue(privacyPolicy.canViewRatingHistory(7L));
        assertTrue(privacyPolicy.canViewAllPostStatuses(null));
        assertTrue(privacyPolicy.isModerator());
    }

    @Test
    void helpActIsVisibleToItsTwoParticipantsAndAdminsOnly() {
        when(authorizationService.getCurrentUser())
                .thenReturn(new AuthorizationService.UserContext(2L, "USER", "h@test.local"));
        assertEquals(PrivacyPolicy.Audience.PARTICIPANT, privacyPolicy.audienceForHelp(2L, 3L));
        assertTrue(privacyPolicy.canViewHelp(2L, 3L));

        when(authorizationService.getCurrentUser())
                .thenReturn(new AuthorizationService.UserContext(9L, "USER", "x@test.local"));
        assertEquals(PrivacyPolicy.Audience.PUBLIC, privacyPolicy.audienceForHelp(2L, 3L));
        assertFalse(privacyPolicy.canViewHelp(2L, 3L));

        when(authorizationService.getCurrentUser())
                .thenReturn(new AuthorizationService.UserContext(99L, "ADMIN", "a@test.local"));
        assertTrue(privacyPolicy.canViewHelp(2L, 3L));
    }

    @Test
    void onlyExplicitlyPublicActivityTypesMayLeaveTheService() {
        assertTrue(privacyPolicy.isPublicActivity(ActivityType.HELP_GIVEN));
        assertTrue(privacyPolicy.isPublicActivity(ActivityType.HELP_RECEIVED));
        assertTrue(privacyPolicy.isPublicActivity(ActivityType.POST_CREATED));
        assertTrue(privacyPolicy.isPublicActivity(ActivityType.ACHIEVEMENT_EARNED));
        assertFalse(privacyPolicy.isPublicActivity(ActivityType.USER_BLOCKED));
    }

    @Test
    void coordinatesAreRoundedForThePublicGrid() {
        assertEquals(53.90, privacyPolicy.publicCoordinate(53.9045));
        assertEquals(30.52, privacyPolicy.publicCoordinate(30.516));
        assertEquals(0.0, privacyPolicy.publicCoordinate(0.0));
        assertNull(privacyPolicy.publicCoordinate(null));
    }
}
