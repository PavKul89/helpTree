package org.example.helptreeservice.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.helptreeservice.config.ChatProperties;
import org.example.helptreeservice.config.PartitionMaintenanceProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.YearMonth;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rolls the monthly partition window forward for {@code messages} and
 * {@code outbox_events} and retires months that are past retention.
 *
 * <p>Creating a month partition whose range already has rows in the default
 * partition is not possible in PostgreSQL: the default must be detached, the
 * new partition created, the overlapping rows moved, and the default attached
 * again. Each table is handled in its own transaction, so a failure on one
 * table neither blocks the other nor leaves a half-detached default partition.
 * A transaction-scoped advisory lock makes the sweep safe when several
 * application instances run the scheduler at the same time.</p>
 *
 * <p>Dropping a messages partition is additionally bounded by
 * {@code app.chat.retention-days}: the effective retention is the larger of the
 * configured partition retention and the chat retention, so this job can never
 * delete data that the product still promises to keep. When chat retention is
 * disabled ({@code 0}) message partitions are never dropped.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PartitionMaintenanceScheduler {

    private static final List<String> TABLES = List.of("messages", "outbox_events");

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final PartitionMaintenanceProperties properties;
    private final ChatProperties chatProperties;

    @Scheduled(cron = "${app.partitions.cron:0 15 3 * * *}")
    @SchedulerLock(name = "PartitionMaintenanceScheduler_maintain",
            lockAtMostFor = "PT1H", lockAtLeastFor = "PT1M")
    public void maintain() {
        if (!properties.isEnabled()) {
            return;
        }

        YearMonth current = YearMonth.now();
        for (String table : TABLES) {
            try {
                // One transaction per table: advisory lock + DDL + row move.
                transactionTemplate.executeWithoutResult(status -> maintainTable(table, current));
            } catch (Exception e) {
                log.error("Partitions: не удалось обслужить таблицу {}", table, e);
            }
        }
    }

    private void maintainTable(String table, YearMonth current) {
        String lockKey = "partition-maintenance:" + table;
        Boolean locked = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(hashtext(?)::bigint)", Boolean.class, lockKey);
        if (!Boolean.TRUE.equals(locked)) {
            log.info("Partitions: {} уже обслуживается другим экземпляром", table);
            return;
        }

        ensureFuturePartitions(table, current);

        if (properties.isDropOldPartitions()) {
            dropOldPartitions(table, current);
        }
    }

    private void ensureFuturePartitions(String table, YearMonth current) {
        int futureMonths = Math.max(0, properties.getFutureMonths());
        for (int offset = 0; offset <= futureMonths; offset++) {
            ensurePartition(table, current.plusMonths(offset));
        }
    }

    private void ensurePartition(String table, YearMonth month) {
        String partition = partitionName(table, month);
        if (relationExists(partition)) {
            return;
        }

        String defaultPartition = table + "_default";
        boolean hasDefault = relationExists(defaultPartition);

        if (hasDefault) {
            // The parent lock taken here makes concurrent inserts wait instead
            // of routing a row into a default partition that is about to change.
            jdbcTemplate.execute("ALTER TABLE " + table
                    + " DETACH PARTITION " + defaultPartition);
        }

        try {
            // The bounds are derived from a YearMonth, never from user input.
            // PostgreSQL does not accept bind parameters in a CREATE TABLE
            // statement, so the values are inlined as ISO dates.
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS " + partition + " PARTITION OF " + table
                            + " FOR VALUES FROM ('" + month.atDay(1)
                            + "') TO ('" + month.plusMonths(1).atDay(1) + "')");

            if (hasDefault) {
                moveDefaultRows(table, defaultPartition, month);
            }
            log.info("Partitions: создана {} на {}", partition, month);
        } finally {
            if (hasDefault) {
                jdbcTemplate.execute("ALTER TABLE " + table
                        + " ATTACH PARTITION " + defaultPartition + " DEFAULT");
            }
        }
    }

    /**
     * Moves rows that belong to the newly created month out of the (detached)
     * default partition. The insert runs first: if it fails, the delete never
     * happens and the transaction rolls the whole sweep back.
     */
    private void moveDefaultRows(String table, String defaultPartition, YearMonth month) {
        jdbcTemplate.update(
                "INSERT INTO " + table + " SELECT * FROM " + defaultPartition
                        + " WHERE created_at >= ? AND created_at < ?",
                month.atDay(1),
                month.plusMonths(1).atDay(1));
        jdbcTemplate.update(
                "DELETE FROM " + defaultPartition
                        + " WHERE created_at >= ? AND created_at < ?",
                month.atDay(1),
                month.plusMonths(1).atDay(1));
    }

    private void dropOldPartitions(String table, YearMonth current) {
        Integer retentionMonths = effectiveRetentionMonths(table);
        if (retentionMonths == null) {
            // Chat retention is explicitly disabled (0); the product promises to
            // keep messages, so partitions must not be dropped either.
            log.info("Partitions: {} сохраняются бессрочно (retention отключён)", table);
            return;
        }
        YearMonth cutoff = current.minusMonths(retentionMonths);
        for (String partition : listMonthPartitions(table)) {
            YearMonth month = partitionMonth(table, partition);
            if (month != null && month.isBefore(cutoff)) {
                jdbcTemplate.execute("ALTER TABLE " + table
                        + " DETACH PARTITION " + partition);
                jdbcTemplate.execute("DROP TABLE IF EXISTS " + partition);
                log.info("Partitions: удалена {} (месяц {}, политика {} мес.)",
                        partition, month, retentionMonths);
            }
        }
    }

    /**
     * The chat retention sweep and the partition drop cannot disagree about how
     * long a message is kept.
     *
     * @return the number of months to keep, or {@code null} when retention is
     *         disabled for that table (chat retention {@code 0} means keep
     *         messages forever).
     */
    private Integer effectiveRetentionMonths(String table) {
        int months = Math.max(1, properties.getRetentionMonths());
        if (!"messages".equals(table)) {
            return months;
        }
        int retentionDays = chatProperties.getRetentionDays();
        if (retentionDays <= 0) {
            return null;
        }
        int chatMonths = (int) Math.ceil(retentionDays / 30.0) + 1;
        return Math.max(months, chatMonths);
    }

    private boolean relationExists(String name) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT to_regclass(?) IS NOT NULL", Boolean.class, name);
        return Boolean.TRUE.equals(exists);
    }

    private List<String> listMonthPartitions(String table) {
        Pattern pattern = monthPattern(table);
        return jdbcTemplate.queryForList(
                        "SELECT child.relname "
                                + "FROM pg_inherits "
                                + "JOIN pg_class parent ON pg_inherits.inhparent = parent.oid "
                                + "JOIN pg_class child ON pg_inherits.inhrelid = child.oid "
                                + "WHERE parent.relname = ? "
                                + "AND parent.relnamespace = current_schema()::regnamespace",
                        String.class, table)
                .stream()
                .filter(name -> pattern.matcher(name).matches())
                .toList();
    }

    private YearMonth partitionMonth(String table, String partition) {
        Matcher matcher = monthPattern(table).matcher(partition);
        if (!matcher.matches()) {
            return null;
        }
        try {
            return YearMonth.of(Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Pattern monthPattern(String table) {
        return Pattern.compile("^" + Pattern.quote(table) + "_(\\d{4})_(\\d{2})$");
    }

    private String partitionName(String table, YearMonth month) {
        return String.format("%s_%04d_%02d", table, month.getYear(), month.getMonthValue());
    }
}
