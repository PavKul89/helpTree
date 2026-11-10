package org.example.helptreeservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.dto.wallet.PaidFeaturePeriodDto;
import org.example.helptreeservice.entity.PaidFeaturePeriod;
import org.example.helptreeservice.enums.PaidFeatureType;
import org.example.helptreeservice.repository.PaidFeaturePeriodRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Append-only history of paid features. Every successful purchase writes one
 * row in the same transaction as the ledger entry, so the history can never
 * disagrees with the wallet.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PaidFeaturePeriodService {

    private final PaidFeaturePeriodRepository repository;

    public void record(Long userId,
                       PaidFeatureType feature,
                       Long referenceId,
                       LocalDateTime startedAt,
                       LocalDateTime endsAt) {
        if (userId == null || feature == null || startedAt == null) {
            throw new IllegalArgumentException("Период платной функции требует пользователя, тип и начало");
        }
        if (endsAt != null && !endsAt.isAfter(startedAt)) {
            throw new IllegalArgumentException("Конец периода должен быть позже начала");
        }

        PaidFeaturePeriod period = PaidFeaturePeriod.builder()
                .userId(userId)
                .feature(feature)
                .referenceId(referenceId)
                .startedAt(startedAt)
                .endsAt(endsAt)
                .createdAt(LocalDateTime.now())
                .build();
        repository.save(period);
        log.info("Записан период {} для пользователя {}: {} — {}",
                feature, userId, startedAt, endsAt);
    }

    @Transactional(readOnly = true)
    public Page<PaidFeaturePeriodDto> getHistory(Long userId, Pageable pageable) {
        LocalDateTime now = LocalDateTime.now();
        return repository.findByUserIdOrderByStartedAtDescIdDesc(userId, pageable)
                .map(period -> PaidFeaturePeriodDto.builder()
                        .id(period.getId())
                        .feature(period.getFeature())
                        .referenceId(period.getReferenceId())
                        .startedAt(period.getStartedAt())
                        .endsAt(period.getEndsAt())
                        .active(period.getEndsAt() == null || period.getEndsAt().isAfter(now))
                        .build());
    }
}
