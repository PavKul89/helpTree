package org.example.helptreeservice.service;

import org.example.helptreeservice.config.HelpProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HelpSlaSchedulerTest {

    @Mock
    private HelpService helpService;

    /**
     * The 50%/80% cutoffs are moments in the past measured from the accepted
     * timeout. In particular the 80% cutoff must be earlier than the 50% one;
     * an inverted formula would send the 80% reminder at 20% elapsed.
     */
    @Test
    void sendSlaReminders_shouldUseElapsedPercentForBothCutoffs() {
        HelpProperties properties = new HelpProperties();
        properties.setAcceptedTimeoutHours(100);
        properties.setReminderFirstPercent(50);
        properties.setReminderSecondPercent(80);
        when(helpService.findHelpIdsForSlaReminder(any(), any(), anyInt()))
                .thenReturn(List.of());

        HelpSlaScheduler scheduler = new HelpSlaScheduler(helpService, properties);
        LocalDateTime before = LocalDateTime.now();
        scheduler.sendSlaReminders();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> first = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> second = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(helpService).findHelpIdsForSlaReminder(first.capture(), second.capture(), anyInt());

        assertTrue(!second.getValue().isAfter(first.getValue()),
                "80% cutoff must not be later than the 50% cutoff");
        assertBetweenHours(before, after, first.getValue(), 50);
        assertBetweenHours(before, after, second.getValue(), 80);
    }

    @Test
    void sendSlaReminders_shouldDoNothingWhenRemindersDisabled() {
        HelpProperties properties = new HelpProperties();
        properties.setRemindersEnabled(false);

        new HelpSlaScheduler(helpService, properties).sendSlaReminders();

        verifyNoInteractions(helpService);
    }

    @Test
    void confirmStaleCompletedHelps_shouldUseCompletedTimeoutAndContinueAfterFailure() {
        HelpProperties properties = new HelpProperties();
        properties.setCompletedTimeoutHours(168);
        properties.setSlaBatchSize(2);
        when(helpService.findStaleCompletedHelpIds(any(), anyInt()))
                .thenReturn(List.of(10L, 11L));
        when(helpService.autoConfirmStaleCompletedHelp(anyLong(), any()))
                .thenThrow(new IllegalStateException("test failure"))
                .thenReturn(true);

        HelpSlaScheduler scheduler = new HelpSlaScheduler(helpService, properties);
        LocalDateTime before = LocalDateTime.now();
        scheduler.confirmStaleCompletedHelps();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> cutoff = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(helpService).findStaleCompletedHelpIds(cutoff.capture(), eq(2));
        verify(helpService).autoConfirmStaleCompletedHelp(10L, cutoff.getValue());
        verify(helpService).autoConfirmStaleCompletedHelp(11L, cutoff.getValue());
        assertBetweenHours(before, after, cutoff.getValue(), 168);
    }

    @Test
    void confirmStaleCompletedHelps_shouldDoNothingWhenDisabled() {
        HelpProperties properties = new HelpProperties();
        properties.setAutoConfirmEnabled(false);

        new HelpSlaScheduler(helpService, properties).confirmStaleCompletedHelps();

        verifyNoInteractions(helpService);
    }

    private void assertBetweenHours(LocalDateTime before,
                                    LocalDateTime after,
                                    LocalDateTime cutoff,
                                    int hoursAgo) {
        long from = Duration.between(before.minusHours(hoursAgo), cutoff).toMinutes();
        long to = Duration.between(after.minusHours(hoursAgo), cutoff).toMinutes();
        assertTrue(from >= 0 && to <= 0,
                "cutoff should be about " + hoursAgo + " hours before now");
    }
}
