package org.example.helptreeservice.block18;

import org.example.helptreeservice.dto.admin.AdminAuditLogDto;
import org.example.helptreeservice.dto.admin.AdminAuditQuery;
import org.example.helptreeservice.dto.admin.AdminAuditStatsDto;
import org.example.helptreeservice.service.AdminAuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-database proof for Block 18.
 *
 * <p>The rows live in a fixed historical window, so the native aggregate
 * queries can be asserted exactly without interference from other tests. All
 * rows use a unique request_id prefix and are removed in {@link #cleanUp()}.</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
class AdminAuditIntegrationTest {

    private static final String EMAIL_PREFIX = "block18-it-";
    private static final String REQUEST_PREFIX = "block18-it-";
    private static final LocalDateTime WINDOW_FROM = LocalDateTime.of(2000, 1, 1, 0, 0);
    private static final LocalDateTime WINDOW_TO = LocalDateTime.of(2000, 1, 2, 0, 0);

    @Autowired private AdminAuditService adminAuditService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Long adminA;
    private Long adminB;

    @BeforeEach
    void setUp() {
        cleanUp();
        adminA = insertUser("admin-a");
        adminB = insertUser("admin-b");
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM admin_audit_log WHERE request_id LIKE ?",
                REQUEST_PREFIX + "%");
        jdbcTemplate.update("DELETE FROM users WHERE email LIKE ?",
                EMAIL_PREFIX + "%@test.local");
    }

    @Test
    void statsShouldAggregateTheBoundedWindowExactly() {
        insert("user-deleted", adminA, "USER_DELETED", "USER", 4L,
                "Блокировка спама");
        insert("post-deleted", adminA, "POST_DELETED", "POST", 5L,
                "Нарушение правил");
        insert("review-deleted", adminB, "REVIEW_DELETED", "REVIEW", 6L,
                "Оскорбление");

        AdminAuditStatsDto stats = adminAuditService.stats(WINDOW_FROM, WINDOW_TO);

        assertEquals(3L, stats.getTotal());
        assertEquals(WINDOW_FROM, stats.getFrom());
        assertEquals(WINDOW_TO, stats.getTo());
        assertEquals(1L, stats.getByAction().get("USER_DELETED"));
        assertEquals(1L, stats.getByAction().get("POST_DELETED"));
        assertEquals(1L, stats.getByAction().get("REVIEW_DELETED"));
        assertEquals(1L, stats.getByEntityType().get("USER"));
        assertEquals(1L, stats.getByEntityType().get("POST"));
        assertEquals(1L, stats.getByEntityType().get("REVIEW"));
        assertEquals(2L, stats.getByAdmin().get(String.valueOf(adminA)));
        assertEquals(1L, stats.getByAdmin().get(String.valueOf(adminB)));
    }

    @Test
    void searchShouldApplyServerSideFilters() {
        insert("user-deleted", adminA, "USER_DELETED", "USER", 4L,
                "Блокировка спама");
        insert("post-deleted", adminA, "POST_DELETED", "POST", 5L,
                "Нарушение правил");

        AdminAuditQuery query = new AdminAuditQuery();
        query.setAction(" post_deleted ");
        query.setFrom(WINDOW_FROM);
        query.setTo(WINDOW_TO);
        Page<AdminAuditLogDto> page = adminAuditService.search(query,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));

        assertEquals(1, page.getTotalElements());
        AdminAuditLogDto row = page.getContent().get(0);
        assertEquals("POST_DELETED", row.getAction());
        assertEquals("POST", row.getEntityType());
        assertEquals(5L, row.getEntityId());
        assertEquals("Нарушение правил", row.getReason());
        assertEquals(adminA, row.getAdminId());
        assertNotNull(row.getBeforeState());
        assertNotNull(row.getAfterState());
    }

    @Test
    void exportShouldReturnOnlyTheFilteredRowsWithEscapedCsv() {
        insert("quoted", adminA, "USER_DELETED", "USER", 4L,
                "Причина с \"кавычками\"");

        AdminAuditQuery query = new AdminAuditQuery();
        query.setFrom(WINDOW_FROM);
        query.setTo(WINDOW_TO);
        String csv = adminAuditService.exportCsv(query);

        String[] lines = csv.split("\\R");
        assertEquals(2, lines.length);
        assertEquals("id,createdAt,adminId,action,entityType,entityId,reason,ipAddress,requestId,beforeState,afterState",
                lines[0]);
        assertTrue(lines[1].contains("\"USER_DELETED\""));
        assertTrue(lines[1].contains("\"Причина с \"\"кавычками\"\"\""));
    }

    private Long insertUser(String suffix) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (name, email, password, created_at, updated_at)
                VALUES (?, ?, ?, now(), now())
                RETURNING id
                """, Long.class, "Block18 " + suffix,
                EMAIL_PREFIX + suffix + "@test.local", "unused");
    }

    private void insert(String suffix,
                        Long adminId,
                        String action,
                        String entityType,
                        Long entityId,
                        String reason) {
        String requestId = REQUEST_PREFIX + suffix;
        jdbcTemplate.update("""
                INSERT INTO admin_audit_log
                    (admin_id, action, entity_type, entity_id, before_state, after_state,
                     reason, ip_address, request_id, created_at)
                VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
                """,
                adminId,
                action,
                entityType,
                entityId,
                "{\"status\":\"OLD\"}",
                "{\"status\":\"NEW\"}",
                reason,
                "127.0.0.1",
                requestId,
                WINDOW_FROM.plusHours(12));
    }
}
