package com.clonezapper.db;

import com.clonezapper.model.ScannedFile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
        file.setId(keys.getKey().longValue());
        return file;
    }

    public Optional<ScannedFile> findById(long id) {
        List<ScannedFile> results = jdbc.query(
            "SELECT * FROM files WHERE id = ?", rowMapper(), id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<ScannedFile> findByScanId(long scanId) {
        return jdbc.query("SELECT * FROM files WHERE scan_id = ?", rowMapper(), scanId);
    }

    /** Files in the same scan that share the same partial hash — candidate duplicates. */
    public List<ScannedFile> findByHashPartial(long scanId, String hashPartial) {
        return jdbc.query(
            "SELECT * FROM files WHERE scan_id = ? AND hash_partial = ?",
            rowMapper(), scanId, hashPartial);
    }

    /** Files in the same scan grouped by size — first filter pass. */
    public List<Map<String, Object>> findSizeGroups(long scanId, int minGroupSize) {
        return jdbc.queryForList(
            "SELECT size, COUNT(*) as cnt FROM files WHERE scan_id = ? GROUP BY size HAVING cnt >= ?",
            scanId, minGroupSize);
    }

    public long countByScanId(long scanId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM files WHERE scan_id = ?", Long.class, scanId);
        return count != null ? count : 0L;
    }

    public long totalBytesByScanId(long scanId) {
        Long total = jdbc.queryForObject("SELECT COALESCE(SUM(size), 0) FROM files WHERE scan_id = ?", Long.class, scanId);
        return total != null ? total : 0L;
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
