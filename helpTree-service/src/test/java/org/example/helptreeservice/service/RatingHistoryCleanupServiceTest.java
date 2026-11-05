package org.example.helptreeservice.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.example.helptreeservice.repository.RatingHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatingHistoryCleanupServiceTest {

    @Mock private RatingHistoryRepository historyRepository;

    private SimpleMeterRegistry meterRegistry;
    private RatingHistoryCleanupService service;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        service = new RatingHistoryCleanupService(historyRepository, meterRegistry);
    }

    @Test
    void deletesAtMostOneBatchAndRecordsTheCounter() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(365);
        when(historyRepository.deleteOldBatch(cutoff, 1000)).thenReturn(250);

        int deleted = service.deleteBatch(cutoff, 1000);

        assertEquals(250, deleted);
        verify(historyRepository).deleteOldBatch(cutoff, 1000);
        assertEquals(250.0, meterRegistry
                .get("helptree.rating.history.deleted").counter().count());
    }

    @Test
    void zeroDeletedRowsDoesNotCreateANoiseCounterSample() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(365);
        when(historyRepository.deleteOldBatch(cutoff, 1000)).thenReturn(0);

        assertEquals(0, service.deleteBatch(cutoff, 1000));
        assertEquals(0, meterRegistry.find("helptree.rating.history.deleted").counters().size());
    }

    @Test
    void rejectsANonPositiveBatchSize() {
        assertThrows(IllegalArgumentException.class,
                () -> service.deleteBatch(LocalDateTime.now(), 0));
    }
}
