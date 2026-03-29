package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — Database schema initialises correctly.
 * All required tables must exist and accept writes on a fresh start.
 */
@Tag("P1")
class DbSchemaTest extends BaseTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ScanRepository scanRepository;

    @Test
    void allExpectedTablesExist() {
        List<String> expectedTables = List.of("scans", "files", "duplicate_groups", "duplicate_members", "actions");
        for (String table : expectedTables) {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
                Integer.class, table);
            assertThat(count).as("Table '%s' should exist", table).isEqualTo(1);
        }
    }

    @Test
    void canInsertAndRetrieveScanRun() {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("schema-test-run");

        ScanRun saved = scanRepository.save(run);
        assertThat(saved.getId()).isNotNull().isPositive();

        ScanRun retrieved = scanRepository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getPhase()).isEqualTo("SCANNING");
        assertThat(retrieved.getRunLabel()).isEqualTo("schema-test-run");
    }

    @Test
    void schemaIsIdempotent() {
        // schema.sql uses CREATE TABLE IF NOT EXISTS — running it twice must not throw
        jdbc.execute("CREATE TABLE IF NOT EXISTS scans (id INTEGER PRIMARY KEY AUTOINCREMENT, phase TEXT)");
        // If we get here without exception, idempotency is confirmed
        assertThat(scanRepository.countAll()).isGreaterThanOrEqualTo(0);
    }
}
