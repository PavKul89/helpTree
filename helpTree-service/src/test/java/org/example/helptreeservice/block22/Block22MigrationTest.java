package org.example.helptreeservice.block22;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 22.8 - clean-database migration rehearsal.
 *
 * <p>Starts an empty PostgreSQL container, migrates through Flyway to V40,
 * loads the representative legacy fixture, then applies every remaining
 * migration and validates the checksums. The test proves that existing rows
 * survive and that the integrity constraints created by V41+ are actually
 * enforced. It runs without the {@code INTEGRATION_TEST} gate and is skipped
 * only when Docker is unavailable.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
class Block22MigrationTest {

    private static final String LATEST_VERSION = "62";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16")
            .withDatabaseName("helptree_migration")
            .withUsername("helptree")
            .withPassword("helptree");

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void migrateLegacyDatabase() throws Exception {
        DataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);

        Flyway toV40 = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target("40")
                .load();
        toV40.migrate();
        toV40.validate();

        assertEquals("40", currentVersion(),
                "The rehearsal must stop at the V40 schema before loading legacy data");

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(fixturePath()));
        }

        Flyway toLatest = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        toLatest.migrate();
        toLatest.validate();

        assertEquals(LATEST_VERSION, currentVersion());
    }

    @Test
    void everyMigrationIsAppliedWithoutFailure() {
        assertEquals(0L, count("SELECT COUNT(*) FROM flyway_schema_history WHERE success = FALSE"));
        assertTrue(historyVersions().contains("40"));
        assertTrue(historyVersions().contains(LATEST_VERSION));
    }

    @Test
    void legacyRowsArePreservedAndNormalized() {
        assertEquals(2L, count("SELECT COUNT(*) FROM users"));
        assertEquals(3.0, jdbc.queryForObject(
                "SELECT rating FROM users WHERE id = 1", Double.class), 0.0001,
                "The legacy 'unrated' 0 must become 3.0");
        assertEquals(0, jdbc.queryForObject(
                "SELECT debt_count FROM users WHERE id = 2", Integer.class),
                "A NULL debt_count must be normalized to 0");
        assertEquals(4.5, jdbc.queryForObject(
                "SELECT rating FROM users WHERE id = 2", Double.class), 0.0001,
                "Valid legacy ratings must not be rewritten");
        assertEquals(10L, jdbc.queryForObject(
                "SELECT help_coins FROM users WHERE id = 2", Long.class));

        assertEquals("Legacy post", jdbc.queryForObject(
                "SELECT title FROM posts WHERE id = 1", String.class));
        assertEquals("COMPLETED", jdbc.queryForObject(
                "SELECT status FROM posts WHERE id = 1", String.class));
        assertEquals("CONFIRMED", jdbc.queryForObject(
                "SELECT status FROM helps WHERE id = 1", String.class));
        assertEquals(2L, count("SELECT COUNT(*) FROM messages WHERE chat_id = 1"));
        assertEquals("Legacy comment", jdbc.queryForObject(
                "SELECT content FROM comments WHERE id = 1", String.class));
        assertEquals(5, jdbc.queryForObject(
                "SELECT rating FROM reviews WHERE id = 1", Integer.class));
        assertEquals(1L, count("SELECT COUNT(*) FROM achievements WHERE user_id = 1"));
        assertEquals(1L, count("SELECT COUNT(*) FROM chats WHERE id = 1"));
    }

    @Test
    void theProjectionTablesExistForTheLegacyRows() {
        // V43/V44 materialise the rating projection, so the migrated facts are
        // immediately visible without waiting for the scheduler.
        assertEquals(1L, count("SELECT COUNT(*) FROM user_rating_stats WHERE user_id = 2"));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT review_count FROM user_rating_stats WHERE user_id = 2", Long.class));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT successful_helps FROM user_rating_stats WHERE user_id = 2", Long.class));
    }

    @Test
    void restoredUniqueIndexesAreActuallyUnique() {
        assertUniqueIndex("uq_users_email_normalized");
        assertUniqueIndex("uq_chats_normalized_pair");
        assertUniqueIndex("uq_helps_active_post");
        assertUniqueIndex("uq_coin_help_given_per_post");
        assertUniqueIndex("uq_reports_open_reporter_target");
        assertUniqueIndex("uq_notifications_user_dedupe");
        assertUniqueIndex("uq_user_blocks_relations_pair");

        assertTrue(indexDefinition("idx_achievements_user_type").contains("CREATE UNIQUE INDEX"),
                "V41 restores the achievements uniqueness under the legacy name");
    }

    @Test
    void restoredConstraintsRejectInvalidLegacyData() {
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO users (name, email, password, rating, debt_count, status, "
                        + "created_at, updated_at, deleted, role, help_coins, token_version, email_verified) "
                        + "VALUES ('Dup', 'LEGACY.RECEIVER@example.test', '!', 3.0, 0, 'ACTIVE', "
                        + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE, 'USER', 0, 0, TRUE)"),
                "Emails are unique after lower(btrim(email)) normalization");

        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE users SET rating = 0 WHERE id = 1"),
                "V41 adds chk_users_rating_range");

        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE users SET debt_count = -1 WHERE id = 1"),
                "V41 adds chk_users_debt_count_non_negative");

        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO chats (user1_id, user2_id, created_at) VALUES (1, 2, CURRENT_TIMESTAMP)"),
                "The normalized chat pair is unique");

        assertThrows(DataAccessException.class, () -> jdbc.update(
                "INSERT INTO helps (post_id, helper_id, receiver_id, status, created_at, updated_at, deleted) "
                        + "VALUES (1, 1, 2, 'ACCEPTED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)"),
                "A post can only have one active help");
    }

    @Test
    void v62CreatedPartitionedStorageForHighVolumeTables() {
        assertTrue(count("SELECT COUNT(*) FROM pg_class WHERE relname = 'outbox_events' "
                + "AND relkind = 'p'") > 0, "outbox_events must be partitioned");
        assertTrue(count("SELECT COUNT(*) FROM pg_class WHERE relname = 'messages' "
                + "AND relkind = 'p'") > 0, "messages must be partitioned");
        assertTrue(count("SELECT COUNT(*) FROM pg_inherits i "
                + "JOIN pg_class child ON child.oid = i.inhrelid "
                + "JOIN pg_class parent ON parent.oid = i.inhparent "
                + "WHERE parent.relname = 'messages'") > 0,
                "V62 must create monthly message partitions");
    }

    // --------------------------------------------------------------- helpers

    private static String currentVersion() {
        return jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE "
                        + "ORDER BY installed_rank DESC LIMIT 1", String.class);
    }

    private static java.util.List<String> historyVersions() {
        return jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE", String.class);
    }

    private static long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value != null ? value : 0L;
    }

    private static String indexDefinition(String name) {
        return jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
                String.class, name);
    }

    private static void assertUniqueIndex(String name) {
        String definition = indexDefinition(name);
        assertTrue(definition != null && definition.contains("UNIQUE"),
                name + " must be a unique index");
    }

    private static Path fixturePath() {
        Path moduleRelative = Path.of("..", "scripts", "fixtures", "legacy_v40_data.sql");
        if (Files.exists(moduleRelative)) {
            return moduleRelative;
        }
        Path repositoryRelative = Path.of("scripts", "fixtures", "legacy_v40_data.sql");
        if (Files.exists(repositoryRelative)) {
            return repositoryRelative;
        }
        throw new IllegalStateException("legacy_v40_data.sql was not found from " + Path.of(".").toAbsolutePath());
    }
}
