package org.example.helptreeservice.service.notification;

import org.example.helptreeservice.config.NotificationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTest {

    @Mock private OutboxService outboxService;
    @Mock private OutboxDeliveryService deliveryService;

    private NotificationProperties properties;
    private OutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        properties.setBatchSize(2);
        properties.setMaxBatchesPerRun(3);
        dispatcher = new OutboxDispatcher(outboxService, deliveryService, properties);
    }

    @Test
    void dispatchDueReleasesStaleClaimsAndDeliversEveryClaimedEvent() {
        OutboxService.OutboxClaim first = new OutboxService.OutboxClaim(10L, 1);
        OutboxService.OutboxClaim second = new OutboxService.OutboxClaim(11L, 1);
        when(outboxService.claimBatch(anyString(), eq(2)))
                .thenReturn(List.of(first, second))
                .thenReturn(List.of());

        dispatcher.dispatchDue();

        verify(outboxService).releaseStaleClaims(any(LocalDateTime.class));
        verify(deliveryService).deliver(10L);
        verify(deliveryService).deliver(11L);
        verify(outboxService, times(2)).claimBatch(anyString(), eq(2));
    }

    @Test
    void aShortBatchEndsTheSweepImmediately() {
        when(outboxService.claimBatch(anyString(), eq(2)))
                .thenReturn(List.of(new OutboxService.OutboxClaim(10L, 1)));

        dispatcher.dispatchDue();

        verify(deliveryService).deliver(10L);
        verify(outboxService, times(1)).claimBatch(anyString(), anyInt());
    }

    @Test
    void anEmptyBatchStopsWithoutDelivery() {
        when(outboxService.claimBatch(anyString(), eq(2))).thenReturn(List.of());

        dispatcher.dispatchDue();

        verifyNoInteractions(deliveryService);
    }

    @Test
    void disabledNotificationsSkipTheWholeSweep() {
        properties.setEnabled(false);

        dispatcher.dispatchDue();
        dispatcher.dispatchImmediately(10L);

        verifyNoInteractions(outboxService, deliveryService);
    }

    @Test
    void aClaimDeliveryFailureDoesNotStopTheRemainingClaims() {
        when(outboxService.claimBatch(anyString(), eq(2)))
                .thenReturn(List.of(
                        new OutboxService.OutboxClaim(10L, 1),
                        new OutboxService.OutboxClaim(11L, 1)))
                .thenReturn(List.of());
        org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
                .when(deliveryService).deliver(10L);

        dispatcher.dispatchDue();

        verify(deliveryService).deliver(10L);
        verify(deliveryService).deliver(11L);
    }

    @Test
    void dispatchImmediatelyDeliversTheClaimedEvent() {
        when(outboxService.claimById(anyString(), eq(10L)))
                .thenReturn(new OutboxService.OutboxClaim(10L, 1));

        dispatcher.dispatchImmediately(10L);

        verify(deliveryService).deliver(10L);
    }

    @Test
    void dispatchImmediatelyDoesNothingWhenAnotherWorkerWonTheClaim() {
        when(outboxService.claimById(anyString(), eq(10L))).thenReturn(null);

        dispatcher.dispatchImmediately(10L);

        verify(deliveryService, never()).deliver(any());
    }

    @Test
    void dispatchImmediatelySwallowsClaimErrors() {
        when(outboxService.claimById(anyString(), eq(10L)))
                .thenThrow(new IllegalStateException("db down"));

        dispatcher.dispatchImmediately(10L);

        verify(deliveryService, never()).deliver(any());
    }

    @Test
    void dispatchImmediatelyIgnoresANullId() {
        dispatcher.dispatchImmediately(null);

        verifyNoInteractions(outboxService, deliveryService);
    }
}
