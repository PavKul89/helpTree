package org.example.helptreeservice.service;

import org.example.helptreeservice.dto.admin.AdminAuditLogDto;
import org.example.helptreeservice.dto.admin.AdminAuditQuery;
import org.example.helptreeservice.dto.admin.AdminAuditStatsDto;
import org.example.helptreeservice.entity.AdminAuditLog;
import org.example.helptreeservice.enums.AdminAuditAction;
import org.example.helptreeservice.repository.AdminAuditLogRepository;
import org.example.helptreeservice.security.ClientInfoResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditServiceTest {

    @Mock private AdminAuditLogRepository auditLogRepository;
    @Mock private ClientInfoResolver clientInfoResolver;

    @InjectMocks private AdminAuditService adminAuditService;

    @Test
    void recordShouldStoreTrimmedActionAndEntity() {
        adminAuditService.record(9L, AdminAuditAction.USER_DELETED,
                AdminAuditService.ENTITY_USER, 4L,
                Map.of("deleted", false), Map.of("deleted", true),
                "  Спам  ");

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AdminAuditLog entry = captor.getValue();
        assertEquals(9L, entry.getAdminId());
        assertEquals("USER_DELETED", entry.getAction());
        assertEquals("USER", entry.getEntityType());
        assertEquals(4L, entry.getEntityId());
        assertEquals("Спам", entry.getReason());
        assertEquals(false, entry.getBeforeState().get("deleted"));
        assertEquals(true, entry.getAfterState().get("deleted"));
    }

    @Test
    void recordShouldTruncateLongReasonToColumnLimit() {
        String reason = "x".repeat(1500);

        adminAuditService.record(9L, AdminAuditAction.POST_DELETED,
                AdminAuditService.ENTITY_POST, 4L, null, null, reason);

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertEquals(1000, captor.getValue().getReason().length());
    }

    @Test
    void recordShouldIgnoreIncompleteCommands() {
        adminAuditService.record(9L, null, AdminAuditService.ENTITY_USER, 4L, null, null, "why");
        adminAuditService.record(9L, AdminAuditAction.USER_DELETED, " ", 4L, null, null, "why");

        verify(auditLogRepository, never()).save(any(AdminAuditLog.class));
    }

    @Test
    void statsShouldDefaultToThirtyDayWindow() {
        when(auditLogRepository.countByAction(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"USER_DELETED", 2L}));
        when(auditLogRepository.countByEntityType(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{"USER", 2L}));
        when(auditLogRepository.countByAdmin(any(), any()))
                .thenReturn(List.<Object[]>of(new Object[]{9L, 2L}));
        when(auditLogRepository.countInWindow(any(), any())).thenReturn(2L);

        AdminAuditStatsDto stats = adminAuditService.stats(null, null);

        assertEquals(2L, stats.getTotal());
        assertEquals(2L, stats.getByAction().get("USER_DELETED"));
        assertEquals(2L, stats.getByEntityType().get("USER"));
        assertEquals(2L, stats.getByAdmin().get("9"));
        assertTrue(stats.getFrom().isBefore(stats.getTo()));
        assertTrue(!stats.getFrom().isAfter(stats.getTo().minusDays(29)));
    }

    @Test
    void exportCsvShouldQuoteValuesAndEscapeQuotes() {
        AdminAuditLog entry = AdminAuditLog.builder()
                .id(1L)
                .createdAt(LocalDateTime.of(2026, 1, 2, 3, 4))
                .adminId(9L)
                .action("USER_DELETED")
                .entityType("USER")
                .entityId(4L)
                .reason("Причина с \"кавычками\"")
                .ipAddress("127.0.0.1")
                .beforeState(Map.of("deleted", false))
                .afterState(Map.of("deleted", true))
                .build();
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        String csv = adminAuditService.exportCsv(new AdminAuditQuery());

        String[] lines = csv.split("\\R");
        assertEquals("id,createdAt,adminId,action,entityType,entityId,reason,ipAddress,requestId,beforeState,afterState", lines[0]);
        assertTrue(lines[1].contains("\"USER_DELETED\""));
        assertTrue(lines[1].contains("\"Причина с \"\"кавычками\"\"\""));
        assertTrue(lines[1].contains("\"127.0.0.1\""));
    }

    @Test
    void searchShouldReturnMappedPage() {
        AdminAuditLog entry = AdminAuditLog.builder()
                .id(1L)
                .createdAt(LocalDateTime.of(2026, 1, 2, 3, 4))
                .action("USER_DELETED")
                .entityType("USER")
                .reason("why")
                .build();
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        AdminAuditQuery query = new AdminAuditQuery();
        query.setAction(" user_deleted ");
        Page<AdminAuditLogDto> page = adminAuditService.search(query, Pageable.unpaged());

        assertEquals(1, page.getTotalElements());
        assertEquals("USER_DELETED", page.getContent().get(0).getAction());
        assertEquals("why", page.getContent().get(0).getReason());
        verify(auditLogRepository).findAll(any(Specification.class), eq(Pageable.unpaged()));
    }
}
