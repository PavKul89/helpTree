package org.example.helptreeservice.controller;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.common.PageResponse;
import org.example.helptreeservice.dto.wallet.AdminCoinAuditDto;
import org.example.helptreeservice.dto.wallet.AdminWalletAccountDto;
import org.example.helptreeservice.dto.wallet.BalanceAnomalyDto;
import org.example.helptreeservice.entity.AdminCoinAudit;
import org.example.helptreeservice.entity.User;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.repository.AdminCoinAuditRepository;
import org.example.helptreeservice.repository.UserRepository;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.WalletReconciliationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/wallet")
@RequiredArgsConstructor
public class AdminWalletController {

    private final AuthorizationService authService;
    private final WalletReconciliationService reconciliationService;
    private final AdminCoinAuditRepository auditRepository;
    private final UserRepository userRepository;
    private final PaginationConfig paginationConfig;

    @GetMapping("/accounts")
    public ResponseEntity<PageResponse<AdminWalletAccountDto>> getAccounts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long minBalance,
            @RequestParam(required = false) Long maxBalance,
            @RequestParam(required = false) Boolean vip,
            @RequestParam(required = false) Boolean blocked,
            @RequestParam(required = false) Boolean debtor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "helpCoins") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        requireAdmin();
        String pattern = StringUtils.hasText(q)
                ? "%" + q.trim().toLowerCase(Locale.ROOT) + "%"
                : null;
        LocalDateTime now = LocalDateTime.now();

        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get("deleted")));
            if (pattern != null) {
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern)));
            }
            if (minBalance != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("helpCoins"), minBalance));
            }
            if (maxBalance != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("helpCoins"), maxBalance));
            }
            if (vip != null) {
                predicates.add(Boolean.TRUE.equals(vip)
                        ? cb.greaterThan(root.get("vipUntil"), now)
                        : cb.or(cb.isNull(root.get("vipUntil")), cb.lessThanOrEqualTo(root.get("vipUntil"), now)));
            }
            if (blocked != null) {
                predicates.add(Boolean.TRUE.equals(blocked)
                        ? cb.greaterThan(root.get("blockedUntil"), now)
                        : cb.or(cb.isNull(root.get("blockedUntil")),
                                cb.lessThanOrEqualTo(root.get("blockedUntil"), now)));
            }
            if (debtor != null) {
                predicates.add(Boolean.TRUE.equals(debtor)
                        ? cb.greaterThan(root.get("debtCount"), 0)
                        : cb.equal(root.get("debtCount"), 0));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<AdminWalletAccountDto> accounts = userRepository
                .findAll(spec, resolveAccountsPageable(page, size, sort, direction))
                .map(this::toAccountDto);
        return ResponseEntity.ok(PageResponse.from(accounts));
    }

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

    private static final Set<String> ACCOUNT_SORTS = Set.of(
            "id", "name", "helpCoins", "debtCount", "createdAt", "rating");

    private Pageable resolveAccountsPageable(int page, int size, String sort, String direction) {
        if (page < 0) {
            throw new BadRequestException("Параметр page не может быть отрицательным");
        }
        if (size < 1) {
            throw new BadRequestException("Параметр size должен быть положительным");
        }
        int resolvedSize = Math.min(size, paginationConfig.getMaxPageSize());
        String property = sort != null && ACCOUNT_SORTS.contains(sort) ? sort : "helpCoins";
        Sort.Direction dir = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return PageRequest.of(page, resolvedSize, Sort.by(dir, property));
    }

    private AdminWalletAccountDto toAccountDto(User user) {
        LocalDateTime now = LocalDateTime.now();
        return AdminWalletAccountDto.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .balance(user.getHelpCoins() == null ? 0L : user.getHelpCoins())
                .debtCount(user.getDebtCount() == null ? 0L : user.getDebtCount().longValue())
                .vipUntil(user.getVipUntil())
                .vipActive(user.getVipUntil() != null && user.getVipUntil().isAfter(now))
                .blockedUntil(user.getBlockedUntil())
                .blocked(user.getBlockedUntil() != null && user.getBlockedUntil().isAfter(now))
                .nicknameColor(user.getNicknameColor())
                .createdAt(user.getCreatedAt())
                .build();
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
