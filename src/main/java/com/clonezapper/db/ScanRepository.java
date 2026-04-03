package com.clonezapper.db;

import com.clonezapper.model.ScanRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Repository
public class ScanRepository {

    private final JdbcTemplate jdbc;

    public ScanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Adds the last_heartbeat column to existing databases created before this
     * column was introduced. Safe to run on new databases too — the exception is
     * swallowed when the column already exists.
     */
    @PostConstruct
    public void migrate() {
        try {
            jdbc.execute("ALTER TABLE scans ADD COLUMN last_heartbeat TEXT");
        } catch (Exception ignored) {
            // Column already exists — safe to ignore
        }
    }

    public ScanRun save(ScanRun run) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO scans (phase, checkpoint_file_id, delta_token, created_at, completed_at, archive_root, run_label) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, run.getPhase());
            ps.setString(2, run.getCheckpointFileId());
            ps.setString(3, run.getDeltaToken());
            ps.setString(4, run.getCreatedAt() != null ? run.getCreatedAt().toString() : LocalDateTime.now().toString());
            ps.setString(5, run.getCompletedAt() != null ? run.getCompletedAt().toString() : null);
            ps.setString(6, run.getArchiveRoot());
            ps.setString(7, run.getRunLabel());
            return ps;
        }, keys);
        run.setId(Objects.requireNonNull(keys.getKey(), "no generated key").longValue());
        return run;
    }

    public void updatePhase(long id, String phase) {
        jdbc.update("UPDATE scans SET phase = ? WHERE id = ?", phase, id);
    }

    public void updateArchiveRoot(long id, String archiveRoot) {
        jdbc.update("UPDATE scans SET archive_root = ? WHERE id = ?", archiveRoot, id);
    }

    /** Stamps the current time as the heartbeat for an active scan. Called every ~10 s. */
    public void updateHeartbeat(long id) {
        jdbc.update("UPDATE scans SET last_heartbeat = ? WHERE id = ?",
            LocalDateTime.now().toString(), id);
    }

    /**
     * Returns the most recent scan that is still in a non-terminal phase.
     * Used by the Dashboard to detect an in-progress or interrupted scan on reconnect.
     */
    public Optional<ScanRun> findInProgress() {
        List<ScanRun> results = jdbc.query(
            "SELECT * FROM scans " +
            "WHERE phase NOT IN ('COMPLETE','FAILED','PURGED','CLEANED','STAGED') " +
            "ORDER BY created_at DESC LIMIT 1",
            rowMapper());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public void markCompleted(long id) {
        jdbc.update("UPDATE scans SET phase = 'COMPLETE', completed_at = ? WHERE id = ?",
            LocalDateTime.now().toString(), id);
    }

    public Optional<ScanRun> findById(long id) {
        List<ScanRun> results = jdbc.query(
            "SELECT * FROM scans WHERE id = ?", rowMapper(), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<ScanRun> findAll() {
        return jdbc.query("SELECT * FROM scans ORDER BY created_at DESC", rowMapper());
    }

    public Optional<ScanRun> findLatest() {
        List<ScanRun> results = jdbc.query(
            "SELECT * FROM scans ORDER BY created_at DESC LIMIT 1", rowMapper());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public long countAll() {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM scans", Long.class);
        return count != null ? count : 0L;
    }

    private RowMapper<ScanRun> rowMapper() {
        return (rs, rowNum) -> {
            ScanRun run = new ScanRun();
            run.setId(rs.getLong("id"));
            run.setPhase(rs.getString("phase"));
            run.setCheckpointFileId(rs.getString("checkpoint_file_id"));
            run.setDeltaToken(rs.getString("delta_token"));
            String createdAt = rs.getString("created_at");
            if (createdAt != null) run.setCreatedAt(LocalDateTime.parse(createdAt));
            String completedAt = rs.getString("completed_at");
            if (completedAt != null) run.setCompletedAt(LocalDateTime.parse(completedAt));
            run.setArchiveRoot(rs.getString("archive_root"));
            run.setRunLabel(rs.getString("run_label"));
            String lastHeartbeat = rs.getString("last_heartbeat");
            if (lastHeartbeat != null) run.setLastHeartbeat(LocalDateTime.parse(lastHeartbeat));
            return run;
        };
    }
}
