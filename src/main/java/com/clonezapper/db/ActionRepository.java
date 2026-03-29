package com.clonezapper.db;

import com.clonezapper.model.Action;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class ActionRepository {

    private final JdbcTemplate jdbc;

    public ActionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Action save(Action action) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO actions (scan_id, action_type, file_id, destination, original_path, executed_at, undone, cleaned, purged) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, action.getScanId());
            ps.setString(2, action.getActionType().name());
            ps.setLong(3, action.getFileId());
            ps.setString(4, action.getDestination());
            ps.setString(5, action.getOriginalPath());
            ps.setString(6, action.getExecutedAt() != null ? action.getExecutedAt().toString() : LocalDateTime.now().toString());
            ps.setInt(7, action.isUndone() ? 1 : 0);
            ps.setInt(8, action.isCleaned() ? 1 : 0);
            ps.setInt(9, action.isPurged() ? 1 : 0);
            return ps;
        }, keys);
        action.setId(keys.getKey().longValue());
        return action;
    }

    public List<Action> findByScanId(long scanId) {
        return jdbc.query("SELECT * FROM actions WHERE scan_id = ?", rowMapper(), scanId);
    }

    public void markCleaned(long scanId) {
        jdbc.update("UPDATE actions SET cleaned = 1 WHERE scan_id = ?", scanId);
    }

    public void markPurged(long scanId) {
        jdbc.update("UPDATE actions SET purged = 1 WHERE scan_id = ?", scanId);
    }

    public long countByScanId(long scanId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM actions WHERE scan_id = ?", Long.class, scanId);
        return count != null ? count : 0L;
    }

    private RowMapper<Action> rowMapper() {
        return (rs, rowNum) -> {
            Action a = new Action();
            a.setId(rs.getLong("id"));
            a.setScanId(rs.getLong("scan_id"));
            a.setActionType(Action.Type.valueOf(rs.getString("action_type")));
            a.setFileId(rs.getLong("file_id"));
            a.setDestination(rs.getString("destination"));
            a.setOriginalPath(rs.getString("original_path"));
            String executedAt = rs.getString("executed_at");
            if (executedAt != null) a.setExecutedAt(LocalDateTime.parse(executedAt));
            a.setUndone(rs.getInt("undone") == 1);
            a.setCleaned(rs.getInt("cleaned") == 1);
            a.setPurged(rs.getInt("purged") == 1);
            return a;
        };
    }
}
