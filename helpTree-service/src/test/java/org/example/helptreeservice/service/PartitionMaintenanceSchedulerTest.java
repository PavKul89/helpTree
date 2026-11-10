package org.example.helptreeservice.service;

import org.example.helptreeservice.config.ChatProperties;
import org.example.helptreeservice.config.PartitionMaintenanceProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartitionMaintenanceSchedulerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;
    @Mock
    private TransactionTemplate transactionTemplate;

    private PartitionMaintenanceProperties properties;
    private ChatProperties chatProperties;
    private PartitionMaintenanceScheduler scheduler;

    @BeforeEach
    void setUp() {
        properties = new PartitionMaintenanceProperties();
        chatProperties = new ChatProperties();
        scheduler = new PartitionMaintenanceScheduler(
                jdbcTemplate, transactionTemplate, properties, chatProperties);

        lenient().doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void disabledSchedulerShouldPerformNoDdl() {
        properties.setEnabled(false);

        scheduler.maintain();

        verifyNoInteractions(jdbcTemplate, transactionTemplate);
    }

    @Test
    void schedulerShouldCreatePartitionsWithInlinedDateBounds() {
        stubLockAndMissingRelations();
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyString()))
                .thenReturn(List.of());

        scheduler.maintain();

        org.mockito.ArgumentCaptor<String> ddl =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).execute(ddl.capture());

        assertTrue(ddl.getAllValues().stream()
                .anyMatch(sql -> sql.contains("PARTITION OF messages")));
        assertTrue(ddl.getAllValues().stream()
                .anyMatch(sql -> sql.contains("PARTITION OF outbox_events")));
        assertTrue(ddl.getAllValues().stream()
                .filter(sql -> sql.contains("FOR VALUES FROM"))
                .allMatch(sql -> sql.contains("FOR VALUES FROM ('")
                        && !sql.contains("?")));
    }

    @Test
    void schedulerShouldNotDropMessagePartitionsWhenChatRetentionIsDisabled() {
        chatProperties.setRetentionDays(0);
        stubLockAndMissingRelations();
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyString()))
                .thenReturn(List.of("messages_2020_01"), List.of());

        scheduler.maintain();

        verify(jdbcTemplate, never())
                .execute(startsWith("ALTER TABLE messages DETACH PARTITION messages_2020_01"));
        verify(jdbcTemplate, never())
                .execute(startsWith("DROP TABLE IF EXISTS messages_2020_01"));
    }

    @Test
    void schedulerShouldDropPartitionsOlderThanRetention() {
        properties.setRetentionMonths(3);
        stubLockAndMissingRelations();
        org.mockito.Mockito.when(jdbcTemplate.queryForList(anyString(), eq(String.class), anyString()))
                .thenReturn(List.of("messages_2020_01"), List.of());

        scheduler.maintain();

        verify(jdbcTemplate)
                .execute("ALTER TABLE messages DETACH PARTITION messages_2020_01");
        verify(jdbcTemplate).execute("DROP TABLE IF EXISTS messages_2020_01");
    }

    private void stubLockAndMissingRelations() {
        when(jdbcTemplate.queryForObject(
                startsWith("SELECT pg_try_advisory_xact_lock"), eq(Boolean.class), anyString()))
                .thenReturn(true);
        when(jdbcTemplate.queryForObject(
                startsWith("SELECT to_regclass"), eq(Boolean.class), anyString()))
                .thenReturn(false);
    }
}
