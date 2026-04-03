package com.clonezapper.service;

import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ExecuteStage;
import com.clonezapper.model.ScanRun;
import com.clonezapper.model.preview.GroupPreviewRow;
import com.clonezapper.model.preview.MemberPreviewRow;
import com.clonezapper.model.preview.PreviewSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds read-only view objects for the Preview screen from existing scan data.
 * Nothing is persisted — all three methods query the DB and assemble records.
 * <p>
 * Typical call sequence per preview screen load:
 *   1. {@link #buildSummary}  — populate the summary bar
 *   2. {@link #buildGroups}   — populate the group table (no members yet)
 *   3. {@link #buildMembers}  — called per group when the user expands a row
 */
@Service
public class PreviewService {

    private final JdbcTemplate jdbc;
    private final ScanRepository scanRepository;
    private final ScanSettings scanSettings;

    public PreviewService(JdbcTemplate jdbc,
                          ScanRepository scanRepository,
                          ScanSettings scanSettings) {
        this.jdbc = jdbc;
        this.scanRepository = scanRepository;
        this.scanSettings = scanSettings;
    }

    // ── Summary bar ───────────────────────────────────────────────────────────

    public PreviewSummary buildSummary(long scanId) {
        double threshold = scanSettings.getConfidenceThreshold();
        ScanRun run = scanRepository.findById(scanId)
            .orElseThrow(() -> new IllegalArgumentException("No scan with id " + scanId));

        long totalFiles = queryLong(
            "SELECT COUNT(*) FROM files WHERE scan_id = ?", scanId);

        Map<String, Object> g = jdbc.queryForMap(
            "SELECT " +
            "  COUNT(*) AS total_groups, " +
            "  SUM(CASE WHEN strategy = 'exact-hash' THEN 1 ELSE 0 END) AS exact_groups, " +
            "  SUM(CASE WHEN strategy != 'exact-hash' THEN 1 ELSE 0 END) AS near_dup_groups, " +
            "  AVG(confidence) AS avg_confidence, " +
            "  SUM(CASE WHEN confidence >= ? THEN 1 ELSE 0 END) AS auto_queue, " +
            "  SUM(CASE WHEN confidence < ? THEN 1 ELSE 0 END) AS review_queue " +
            "FROM duplicate_groups WHERE scan_id = ?",
            threshold, threshold, scanId);

        long reclaimable = queryLong(
            "SELECT COALESCE(SUM(f.size), 0) " +
            "FROM duplicate_members dm " +
            "JOIN duplicate_groups dg ON dm.group_id = dg.id " +
            "JOIN files f ON dm.file_id = f.id " +
            "WHERE dg.scan_id = ? AND dm.file_id != dg.canonical_file_id",
            scanId);

        long totalDupes = queryLong(
            "SELECT COUNT(*) " +
            "FROM duplicate_members dm " +
            "JOIN duplicate_groups dg ON dm.group_id = dg.id " +
            "WHERE dg.scan_id = ? AND dm.file_id != dg.canonical_file_id",
            scanId);

        String archiveRoot = run.getArchiveRoot() != null ? run.getArchiveRoot() : "";

        return new PreviewSummary(
            totalFiles,
            toLong(g.get("total_groups")).intValue(),
            toLong(g.get("exact_groups")).intValue(),
            toLong(g.get("near_dup_groups")).intValue(),
            (int) totalDupes,
            reclaimable,
            toDouble(g.get("avg_confidence")),
            toLong(g.get("auto_queue")).intValue(),
            toLong(g.get("review_queue")).intValue(),
            archiveRoot,
            archiveFreeBytes(archiveRoot)
        );
    }

    // ── Group table ───────────────────────────────────────────────────────────

    /**
     * Returns one {@link GroupPreviewRow} per duplicate group, sorted by confidence ASC
     * (lowest confidence first — surfaces items needing review at the top).
     * The {@code members} list on each row is empty; call {@link #buildMembers} to populate.
     */
    public List<GroupPreviewRow> buildGroups(long scanId) {
        return jdbc.query(
            "SELECT " +
            "  dg.id AS group_id, dg.strategy, dg.confidence, dg.canonical_file_id, " +
            "  cf.path AS canonical_path, cf.mime_type AS canonical_mime_type, " +
            "  COUNT(dm.file_id) AS member_count, " +
            "  COUNT(dm.file_id) - 1 AS dupe_count, " +
            "  COALESCE(SUM(CASE WHEN dm.file_id != dg.canonical_file_id THEN f.size ELSE 0 END), 0) AS reclaimable_bytes, " +
            "  COALESCE(SUM(f.size), 0) AS total_bytes " +
            "FROM duplicate_groups dg " +
            "LEFT JOIN files cf ON dg.canonical_file_id = cf.id " +
            "JOIN duplicate_members dm ON dm.group_id = dg.id " +
            "JOIN files f ON dm.file_id = f.id " +
            "WHERE dg.scan_id = ? " +
            "GROUP BY dg.id, dg.strategy, dg.confidence, dg.canonical_file_id, cf.path, cf.mime_type " +
            "ORDER BY dg.confidence ASC",
            (rs, rowNum) -> new GroupPreviewRow(
                rs.getLong("group_id"),
                rs.getString("strategy"),
                rs.getDouble("confidence"),
                rs.getInt("member_count"),
                rs.getInt("dupe_count"),
                rs.getLong("reclaimable_bytes"),
                rs.getLong("total_bytes"),
                rs.getString("canonical_path"),
                rs.getString("canonical_mime_type"),
                new ArrayList<>()
            ),
            scanId);
    }

    // ── Member drill-down ─────────────────────────────────────────────────────

    /**
     * Returns one {@link MemberPreviewRow} per file in the group.
     * The canonical file is listed first; all others include a {@code proposedArchivePath}.
     */
    public List<MemberPreviewRow> buildMembers(long groupId) {
        Map<String, Object> groupInfo = jdbc.queryForMap(
            "SELECT scan_id, canonical_file_id FROM duplicate_groups WHERE id = ?", groupId);
        long scanId = toLong(groupInfo.get("scan_id"));
        long canonicalFileId = toLong(groupInfo.get("canonical_file_id"));

        ScanRun run = scanRepository.findById(scanId)
            .orElseThrow(() -> new IllegalArgumentException("No scan with id " + scanId));
        String archiveRoot = run.getArchiveRoot() != null ? run.getArchiveRoot() : "";

        return jdbc.query(
            "SELECT dm.file_id, dm.confidence, f.path, f.size, f.modified_at, f.mime_type " +
            "FROM duplicate_members dm " +
            "JOIN files f ON dm.file_id = f.id " +
            "WHERE dm.group_id = ? " +
            "ORDER BY (dm.file_id = ?) DESC",   // canonical row first
            (rs, rowNum) -> {
                long fileId = rs.getLong("file_id");
                boolean isCanonical = fileId == canonicalFileId;
                String path = rs.getString("path");
                String modStr = rs.getString("modified_at");
                LocalDateTime modifiedAt = modStr != null ? LocalDateTime.parse(modStr) : null;
                String archivePath = isCanonical ? null
                    : ExecuteStage.buildArchivePath(archiveRoot, scanId, path).toString();
                return new MemberPreviewRow(
                    fileId,
                    path,
                    rs.getLong("size"),
                    modifiedAt,
                    rs.getString("mime_type"),
                    rs.getDouble("confidence"),
                    isCanonical,
                    archivePath
                );
            },
            groupId, canonicalFileId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Walk up to the nearest existing ancestor to check available space. */
    private long archiveFreeBytes(String archiveRoot) {
        if (archiveRoot == null || archiveRoot.isBlank()) return -1L;
        try {
            File f = new File(archiveRoot);
            while (f != null && !f.exists()) f = f.getParentFile();
            return f != null ? f.getFreeSpace() : -1L;
        } catch (Exception e) {
            return -1L;
        }
    }

    private long queryLong(String sql, Object... args) {
        return Objects.requireNonNullElse(
            jdbc.queryForObject(sql, Long.class, args), 0L);
    }

    private Long toLong(Object val) {
        return switch (val) {
            case null     -> 0L;
            case Long l   -> l;
            case Number n -> n.longValue();
            default       -> 0L;
        };
    }

    private double toDouble(Object val) {
        return switch (val) {
            case null     -> 0.0;
            case Double d -> d;
            case Number n -> n.doubleValue();
            default       -> 0.0;
        };
    }
}
