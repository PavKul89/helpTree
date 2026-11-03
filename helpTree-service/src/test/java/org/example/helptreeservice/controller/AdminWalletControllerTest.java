package org.example.helptreeservice.controller;

import org.example.helptreeservice.config.PaginationConfig;
import org.example.helptreeservice.dto.wallet.BalanceAnomalyDto;
import org.example.helptreeservice.entity.AdminCoinAudit;
import org.example.helptreeservice.exception.BadRequestException;
import org.example.helptreeservice.exception.ForbiddenException;
import org.example.helptreeservice.repository.AdminCoinAuditRepository;
import org.example.helptreeservice.service.AuthorizationService;
import org.example.helptreeservice.service.WalletReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminWalletControllerTest {

    @Mock private AuthorizationService authService;
    @Mock private WalletReconciliationService reconciliationService;
    @Mock private AdminCoinAuditRepository auditRepository;
    @Mock private PaginationConfig paginationConfig;

    @InjectMocks private AdminWalletController controller;

    @BeforeEach
    void denyByDefault() {
        lenient().when(authService.isAdmin()).thenReturn(false);
    }

    @Test
    void reconciliationShouldForbidNonAdmin() {
        assertThrows(ForbiddenException.class, () -> controller.getReconciliation());
        verifyNoInteractions(reconciliationService);
    }

    @Test
    void auditsShouldForbidNonAdmin() {
        assertThrows(ForbiddenException.class,
                () -> controller.getAudits(0, 20, null, null));
        verifyNoInteractions(auditRepository);
    }

    @Test
    void reconciliationShouldReturnAnomaliesToAdmin() {
        when(authService.isAdmin()).thenReturn(true);
        when(reconciliationService.findAnomalies()).thenReturn(List.of(
                BalanceAnomalyDto.builder()
                        .userId(5L)
                        .balance(10L)
                        .ledgerTotal(9L)
                        .difference(1L)
                        .detectedAt(LocalDateTime.now())
                        .build()));

        var response = controller.getReconciliation();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, response.getBody().size());
        assertEquals(5L, response.getBody().get(0).getUserId());
    }

    @Test
    void auditsShouldClampPageSizeAndFilterByUser() {
        when(authService.isAdmin()).thenReturn(true);
        when(paginationConfig.getMaxPageSize()).thenReturn(100);
        when(auditRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class))).thenReturn(Page.empty());

        controller.getAudits(0, 5000, 7L, 99L);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditRepository).findAll(
                any(org.springframework.data.jpa.domain.Specification.class), captor.capture());
        assertEquals(100, captor.getValue().getPageSize());
    }

    @Test
    void auditsShouldRejectInvalidPagination() {
        when(authService.isAdmin()).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> controller.getAudits(-1, 20, null, null));
        assertThrows(BadRequestException.class,
                () -> controller.getAudits(0, 0, null, null));
    }

    @Test
    void auditsShouldMapAuditRows() {
        when(authService.isAdmin()).thenReturn(true);
        when(paginationConfig.getMaxPageSize()).thenReturn(100);
        AdminCoinAudit audit = AdminCoinAudit.builder()
                .id(1L)
                .adminId(2L)
                .userId(3L)
                .amount(-50L)
                .balanceBefore(100L)
                .balanceAfter(50L)
                .reason("Correction")
                .transactionId(4L)
                .createdAt(LocalDateTime.now())
                .build();
        when(auditRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(Pageable.class))).thenReturn(new PageImpl<>(List.of(audit)));

        var response = controller.getAudits(0, 20, 3L, null);

        assertEquals(1, response.getBody().getContent().size());
        assertEquals(-50L, response.getBody().getContent().get(0).getAmount());
        assertEquals("Correction", response.getBody().getContent().get(0).getReason());
    }
}
