package org.example.helptreeservice.repository;

import org.example.helptreeservice.entity.AdminCoinAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AdminCoinAuditRepository extends JpaRepository<AdminCoinAudit, Long>,
        JpaSpecificationExecutor<AdminCoinAudit> {

    Page<AdminCoinAudit> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<AdminCoinAudit> findByAdminIdOrderByCreatedAtDesc(Long adminId, Pageable pageable);
}
