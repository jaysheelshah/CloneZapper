package com.clonezapper.db;

import com.clonezapper.model.ScannedFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Repository
public class FileRepository {

    private final JdbcTemplate jdbc;

    public FileRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ScannedFile save(ScannedFile file) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO files (scan_id, path, provider, size, modified_at, mime_type, hash_partial, hash_full, minhash_signature, copy_hint) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, file.getScanId());
            ps.setString(2, file.getPath());
            ps.setString(3, file.getProvider());
            ps.setLong(4, file.getSize());
            ps.setString(5, file.getModifiedAt() != null ? file.getModifiedAt().toString() : null);
            ps.setString(6, file.getMimeType());
            ps.setString(7, file.getHashPartial());
            ps.setString(8, file.getHashFull());
            ps.setBytes(9, file.getMinhashSignature());
            ps.setString(10, file.getCopyHint());
            return ps;
        }, keys);
        file.setId(Objects.requireNonNull(keys.getKey(), "no generated key").longValue());
        return file;
    }

    public Optional<ScannedFile> findById(long id) {
        List<ScannedFile> results = jdbc.query(
            "SELECT * FROM files WHERE id = ?", rowMapper(), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    public List<ScannedFile> findByScanId(long scanId) {
        return jdbc.query("SELECT * FROM files WHERE scan_id = ?", rowMapper(), scanId);
    }

    /** Files in the same scan grouped by size — first filter pass. */
    public List<Map<String, Object>> findSizeGroups(long scanId, int minGroupSize) {
        return jdbc.queryForList(
            "SELECT size, COUNT(*) as cnt FROM files WHERE scan_id = ? GROUP BY size HAVING cnt >= ?",
            scanId, minGroupSize);
    }

    /** All files in a scan with a given size — used by CandidateStage for partial-hash grouping. */
    public List<ScannedFile> findByScanIdAndSize(long scanId, long size) {
        return jdbc.query(
            "SELECT * FROM files WHERE scan_id = ? AND size = ?",
            rowMapper(), scanId, size);
    }

    /**
     * Most recent scan record for a given absolute path — used by ScanStage for incremental
     * scanning (reuse existing fingerprints when size + modified_at are unchanged).
     */
    public Optional<ScannedFile> findLatestByPath(String path) {
        List<ScannedFile> results = jdbc.query(
            "SELECT * FROM files WHERE path = ? ORDER BY id DESC LIMIT 1",
            rowMapper(), path);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.getFirst());
    }

    /**
     * Bulk-load the most recent fingerprint record for every known path — one query
     * instead of N per-file round-trips.  Used by ScanStage as an in-memory cache that
     * replaces per-file {@link #findLatestByPath} calls.
     * Only paths that already have a partial hash are included (files that were fully
     * processed in a previous scan).
     */
    public Map<String, ScannedFile> loadLatestByPath() {
        List<ScannedFile> records = jdbc.query(
            "SELECT f.* FROM files f " +
            "JOIN (SELECT path, MAX(id) AS max_id FROM files GROUP BY path) latest " +
            "ON f.id = latest.max_id " +
            "WHERE f.hash_partial IS NOT NULL",
            rowMapper());
        Map<String, ScannedFile> map = new HashMap<>(records.size() * 2);
        for (ScannedFile f : records) map.put(f.getPath(), f);
        return map;
    }

    /**
     * Batch-insert a list of file records in a single JDBC batch (one SQLite transaction).
     * Much faster than N individual {@link #save} calls for large scans.
     * Generated IDs are not set on the returned objects — callers that need IDs
     * should query by scan_id after this call.
     */
    public void saveBatch(List<ScannedFile> files) {
        if (files.isEmpty()) return;
        jdbc.batchUpdate(
            "INSERT INTO files (scan_id, path, provider, size, modified_at, mime_type, " +
            "hash_partial, hash_full, minhash_signature, copy_hint) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ScannedFile f = files.get(i);
                    ps.setLong(1, f.getScanId());
                    ps.setString(2, f.getPath());
                    ps.setString(3, f.getProvider());
                    ps.setLong(4, f.getSize());
                    ps.setString(5, f.getModifiedAt() != null ? f.getModifiedAt().toString() : null);
                    ps.setString(6, f.getMimeType());
                    ps.setString(7, f.getHashPartial());
                    ps.setString(8, f.getHashFull());
                    ps.setBytes(9, f.getMinhashSignature());
                    ps.setString(10, f.getCopyHint());
                }
                @Override
                public int getBatchSize() { return files.size(); }
            });
    }

    /**
     * Load a lightweight {@code path → "size|modifiedAt"} snapshot for all files in a scan.
     * Used by {@link com.clonezapper.engine.UnifiedScanner} to quickly detect whether
     * anything changed since the previous scan, without loading full fingerprint data.
     */
    public Map<String, String> loadSnapshotByScanId(long scanId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT path, size, modified_at FROM files WHERE scan_id = ?", scanId);
        Map<String, String> snapshot = new HashMap<>(rows.size() * 2);
        for (Map<String, Object> row : rows) {
            String path   = (String) row.get("path");
            long   size   = ((Number) row.get("size")).longValue();
            String modRaw = (String) row.get("modified_at");
            String mod    = modRaw != null
                ? LocalDateTime.parse(modRaw).truncatedTo(ChronoUnit.SECONDS).toString()
                : "";
            snapshot.put(path, size + "|" + mod);
        }
        return snapshot;
    }

    public void updateHashFull(long id, String hashFull) {
        jdbc.update("UPDATE files SET hash_full = ? WHERE id = ?", hashFull, id);
    }

    public void updateMinhashSignature(long id, byte[] sig) {
        jdbc.update("UPDATE files SET minhash_signature = ? WHERE id = ?", sig, id);
    }

    /** All files in a scan with a given MIME type — used by CompareStage for near-dup grouping. */
    public List<ScannedFile> findByScanIdAndMimeType(long scanId, String mimeType) {
        return jdbc.query(
            "SELECT * FROM files WHERE scan_id = ? AND mime_type = ?",
            rowMapper(), scanId, mimeType);
    }

    /** Distinct MIME types present in a scan — used to dispatch near-dup handlers. */
    public List<String> findDistinctMimeTypesByScanId(long scanId) {
        return jdbc.queryForList(
            "SELECT DISTINCT mime_type FROM files WHERE scan_id = ? AND mime_type IS NOT NULL",
            String.class, scanId);
    }

    public long countByScanId(long scanId) {
        return Objects.requireNonNull(
            jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE scan_id = ?", Long.class, scanId));
    }

    public long totalBytesByScanId(long scanId) {
        return Objects.requireNonNullElse(
            jdbc.queryForObject("SELECT COALESCE(SUM(size), 0) FROM files WHERE scan_id = ?", Long.class, scanId), 0L);
    }

    private RowMapper<ScannedFile> rowMapper() {
        return (rs, rowNum) -> {
            ScannedFile.Builder b = ScannedFile.builder()
                .id(rs.getLong("id"))
                .scanId(rs.getLong("scan_id"))
                .path(rs.getString("path"))
                .provider(rs.getString("provider"))
                .size(rs.getLong("size"))
                .mimeType(rs.getString("mime_type"))
                .hashPartial(rs.getString("hash_partial"))
                .hashFull(rs.getString("hash_full"))
                .minhashSignature(rs.getBytes("minhash_signature"))
                .copyHint(rs.getString("copy_hint"));
            String modifiedAt = rs.getString("modified_at");
            if (modifiedAt != null) b.modifiedAt(LocalDateTime.parse(modifiedAt));
            return b.build();
        };
    }
}
