package org.example.helptreeservice.service;

import org.example.helptreeservice.entity.PaidFeaturePeriod;
import org.example.helptreeservice.enums.PaidFeatureType;
import org.example.helptreeservice.repository.PaidFeaturePeriodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaidFeaturePeriodServiceTest {

    @Mock
    private PaidFeaturePeriodRepository repository;

    @Test
    void recordShouldPersistValidPeriod() {
        PaidFeaturePeriodService service = new PaidFeaturePeriodService(repository);
        LocalDateTime start = LocalDateTime.now();

        service.record(1L, PaidFeatureType.VIP, 10L, start, start.plusDays(30));

        ArgumentCaptor<PaidFeaturePeriod> captor =
                ArgumentCaptor.forClass(PaidFeaturePeriod.class);
        verify(repository).save(captor.capture());
        assertEquals(1L, captor.getValue().getUserId());
        assertEquals(PaidFeatureType.VIP, captor.getValue().getFeature());
        assertEquals(10L, captor.getValue().getReferenceId());
        assertEquals(start, captor.getValue().getStartedAt());
        assertEquals(start.plusDays(30), captor.getValue().getEndsAt());
    }

    @Test
    void recordShouldAllowOpenEndedNicknameColor() {
        PaidFeaturePeriodService service = new PaidFeaturePeriodService(repository);

        service.record(1L, PaidFeatureType.NICKNAME_COLOR, 10L, LocalDateTime.now(), null);

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(
                period -> period.getEndsAt() == null));
    }

    @Test
    void recordShouldRejectInvalidArguments() {
        PaidFeaturePeriodService service = new PaidFeaturePeriodService(repository);
        LocalDateTime now = LocalDateTime.now();

        assertThrows(IllegalArgumentException.class,
                () -> service.record(null, PaidFeatureType.VIP, 1L, now, now.plusDays(1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.record(1L, null, 1L, now, now.plusDays(1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.record(1L, PaidFeatureType.VIP, 1L, null, now.plusDays(1)));
        assertThrows(IllegalArgumentException.class,
                () -> service.record(1L, PaidFeatureType.VIP, 1L, now, now));
    }

    @Test
    void historyShouldMarkPeriodsByTheirEndDate() {
        PaidFeaturePeriodService service = new PaidFeaturePeriodService(repository);
        Pageable pageable = PageRequest.of(0, 20);
        PaidFeaturePeriod expired = PaidFeaturePeriod.builder()
                .id(1L)
                .feature(PaidFeatureType.BOOST)
                .startedAt(LocalDateTime.now().minusDays(10))
                .endsAt(LocalDateTime.now().minusDays(9))
                .build();
        PaidFeaturePeriod active = PaidFeaturePeriod.builder()
                .id(2L)
                .feature(PaidFeatureType.VIP)
                .startedAt(LocalDateTime.now().minusDays(1))
                .endsAt(LocalDateTime.now().plusDays(1))
                .build();
        PaidFeaturePeriod open = PaidFeaturePeriod.builder()
                .id(3L)
                .feature(PaidFeatureType.NICKNAME_COLOR)
                .startedAt(LocalDateTime.now().minusDays(1))
                .endsAt(null)
                .build();
        when(repository.findByUserIdOrderByStartedAtDescIdDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(expired, active, open), pageable, 3));

        var page = service.getHistory(1L, pageable);

        assertEquals(3, page.getTotalElements());
        assertFalse(page.getContent().get(0).isActive());
        assertTrue(page.getContent().get(1).isActive());
        assertTrue(page.getContent().get(2).isActive());
    }
}
