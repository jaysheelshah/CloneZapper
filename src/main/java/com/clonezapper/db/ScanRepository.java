package com.clonezapper.db;

import com.clonezapper.model.ScanRun;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ScanRepository {

    private final JdbcTemplate jdbc;

    public ScanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
        run.setId(keys.getKey().longValue());
        return run;
    }

    public void updatePhase(long id, String phase) {
        jdbc.update("UPDATE scans SET phase = ? WHERE id = ?", phase, id);
    }

    public void markCompleted(long id) {
        jdbc.update("UPDATE scans SET phase = 'COMPLETE', completed_at = ? WHERE id = ?",
            LocalDateTime.now().toString(), id);
    }

    public Optional<ScanRun> findById(long id) {
        List<ScanRun> results = jdbc.query(
            "SELECT * FROM scans WHERE id = ?", rowMapper(), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<ScanRun> findAll() {
        return jdbc.query("SELECT * FROM scans ORDER BY created_at DESC", rowMapper());
    }

    public Optional<ScanRun> findLatest() {
        List<ScanRun> results = jdbc.query(
            "SELECT * FROM scans ORDER BY created_at DESC LIMIT 1", rowMapper());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
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
            return run;
        };
    }
}
