package org.example.helptreeservice.service;

import org.example.helptreeservice.entity.Help;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.enums.AchievementType;
import org.example.helptreeservice.enums.HelpStatus;
import org.example.helptreeservice.repository.AchievementRepository;
import org.example.helptreeservice.repository.HelpRepository;
import org.example.helptreeservice.repository.MessageRepository;
import org.example.helptreeservice.repository.PostRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AchievementServiceTest {

    @Mock private AchievementRepository achievementRepository;
    @Mock private HelpRepository helpRepository;
    @Mock private PostRepository postRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationService notificationService;

    private AchievementService service;

    @BeforeEach
    void setUp() {
        service = new AchievementService(
                achievementRepository,
                helpRepository,
                postRepository,
                messageRepository,
                userRepository,
                notificationService
        );
    }

    private User user() {
        User user = new User();
        user.setId(1L);
        user.setName("Helper");
        user.setDeleted(false);
        user.setHelpedCount(0);
        user.setDebtCount(0);
        user.setRating(3.0);
        return user;
    }

    private Help confirmedHelp(User helper) {
        User receiver = new User();
        receiver.setId(2L);
        receiver.setName("Receiver");
        Help help = new Help();
        help.setId(10L);
        help.setHelper(helper);
        help.setReceiver(receiver);
        help.setStatus(HelpStatus.CONFIRMED);
        help.setConfirmedAt(LocalDateTime.now());
        return help;
    }

    @Test
    void shouldAwardFromConfirmedFactsNotOperationalCounter() {
        User user = user();
        when(helpRepository.countByHelperIdAndStatus(1L, HelpStatus.CONFIRMED)).thenReturn(1L);
        when(achievementRepository.findAchievementTypesByUserId(1L)).thenReturn(Set.of());
        when(achievementRepository.insertIfAbsent(eq(1L), any(), any())).thenReturn(1);

        service.checkAndAwardAchievements(user, confirmedHelp(user));

        // users.helped_count is 0, but the confirmed fact awards FIRST_HELP.
        verify(achievementRepository).insertIfAbsent(eq(1L), eq(AchievementType.FIRST_HELP.name()), any());
        verify(achievementRepository, never())
                .insertIfAbsent(eq(1L), eq(AchievementType.HELPER_5.name()), any());
    }

    @Test
    void shouldNotAwardExistingAchievementAgain() {
        User user = user();
        when(helpRepository.countByHelperIdAndStatus(1L, HelpStatus.CONFIRMED)).thenReturn(10L);
        // First run: nothing exists, so thresholds up to HELPER_10 are awarded.
        when(achievementRepository.findAchievementTypesByUserId(1L))
                .thenReturn(Set.of())
                .thenReturn(Set.of(AchievementType.FIRST_HELP, AchievementType.HELPER_5,
                        AchievementType.HELPER_10));
        when(achievementRepository.insertIfAbsent(eq(1L), any(), any())).thenReturn(1);

        service.checkAndAwardAchievements(user, confirmedHelp(user));
        service.checkAndAwardAchievements(user, confirmedHelp(user));

        verify(achievementRepository, times(1))
                .insertIfAbsent(eq(1L), eq(AchievementType.FIRST_HELP.name()), any());
        verify(achievementRepository, times(1))
                .insertIfAbsent(eq(1L), eq(AchievementType.HELPER_10.name()), any());
    }

    @Test
    void progressShouldUseConfirmedHelps() {
        User user = user();
        user.setHelpedCount(0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(helpRepository.countByHelperIdAndStatus(1L, HelpStatus.CONFIRMED)).thenReturn(10L);
        when(helpRepository.findByHelperAndStatus(user, HelpStatus.CONFIRMED)).thenReturn(List.of());

        var achievements = service.getAllAchievementTypesWithProgress(1L);

        var helper10 = achievements.stream()
                .filter(a -> a.getType().equals(AchievementType.HELPER_10.name()))
                .findFirst()
                .orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(10, helper10.getCurrentProgress());
        org.junit.jupiter.api.Assertions.assertEquals(10, helper10.getTargetValue());
    }
}
