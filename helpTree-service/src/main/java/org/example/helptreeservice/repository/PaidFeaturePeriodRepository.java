package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.PaidFeaturePeriod;
import org.example.helptreeservice.enums.PaidFeatureType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaidFeaturePeriodRepository extends JpaRepository<PaidFeaturePeriod, Long> {

    Page<PaidFeaturePeriod> findByUserIdOrderByStartedAtDescIdDesc(Long userId, Pageable pageable);

    boolean existsByUserIdAndFeatureAndReferenceId(Long userId, PaidFeatureType feature, Long referenceId);
}
