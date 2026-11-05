package org.example.helptreeservice.controller;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.dto.wallet.AdminCoinAuditDto;
import org.example.helptreeservice.dto.wallet.BalanceAnomalyDto;
import org.example.helptreeservice.entity.AdminCoinAudit;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.repository.AdminCoinAuditRepository;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.WalletReconciliationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/wallet")
@RequiredArgsConstructor
public class AdminWalletController {

    private final AuthorizationService authService;
    private final WalletReconciliationService reconciliationService;
    private final AdminCoinAuditRepository auditRepository;
    private final PaginationConfig paginationConfig;

    @GetMapping("/reconciliation")
    public ResponseEntity<PageResponse<BalanceAnomalyDto>> getReconciliation(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin();
        Pageable pageable = resolveReconciliationPageable(page, size);
        return ResponseEntity.ok(PageResponse.from(
                reconciliationService.findAnomalies(pageable)));
    }

    @GetMapping("/audits")
    public ResponseEntity<Page<AdminCoinAuditDto>> getAudits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long adminId) {
        requireAdmin();

        Pageable pageable = resolvePageable(page, size);
        Specification<AdminCoinAudit> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (adminId != null) {
                predicates.add(cb.equal(root.get("adminId"), adminId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return ResponseEntity.ok(auditRepository.findAll(spec, pageable).map(this::toDto));
    }

    private void requireAdmin() {
        if (!authService.isAdmin()) {
            throw new ForbiddenException("Только администратор может просматривать аудит кошельков");
        }
    }

    private Pageable resolvePageable(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Параметр page не может быть отрицательным");
        }
        if (size < 1) {
            throw new BadRequestException("Параметр size должен быть положительным");
        }
        int resolvedSize = Math.min(size, paginationConfig.getMaxPageSize());
        return PageRequest.of(page, resolvedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    /**
     * Anomalies are aggregated by user id; the native query already orders by
     * it, so the Pageable must stay unsorted.
     */
    private Pageable resolveReconciliationPageable(int page, int size) {
        if (page < 0) {
            throw new BadRequestException("Параметр page не может быть отрицательным");
        }
        if (size < 1) {
            throw new BadRequestException("Параметр size должен быть положительным");
        }
        int resolvedSize = Math.min(size, paginationConfig.getMaxPageSize());
        return PageRequest.of(page, resolvedSize);
    }

    private AdminCoinAuditDto toDto(AdminCoinAudit audit) {
        return AdminCoinAuditDto.builder()
                .id(audit.getId())
                .adminId(audit.getAdminId())
                .userId(audit.getUserId())
                .amount(audit.getAmount())
                .balanceBefore(audit.getBalanceBefore())
                .balanceAfter(audit.getBalanceAfter())
                .reason(audit.getReason())
                .transactionId(audit.getTransactionId())
                .createdAt(audit.getCreatedAt())
                .build();
    }
}
