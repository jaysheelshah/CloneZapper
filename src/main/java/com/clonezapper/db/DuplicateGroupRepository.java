package com.clonezapper.db;

import com.clonezapper.model.DuplicateGroup;
import com.clonezapper.model.DuplicateMember;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Objects;

@Repository
public class DuplicateGroupRepository {

    private final JdbcTemplate jdbc;

    public DuplicateGroupRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DuplicateGroup save(DuplicateGroup group) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO duplicate_groups (scan_id, canonical_file_id, strategy, confidence) " +
                "VALUES (?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, group.getScanId());
            if (group.getCanonicalFileId() != null) ps.setLong(2, group.getCanonicalFileId());
            else ps.setNull(2, Types.INTEGER);
            ps.setString(3, group.getStrategy());
            ps.setDouble(4, group.getConfidence());
            return ps;
        }, keys);
        group.setId(Objects.requireNonNull(keys.getKey(), "no generated key").longValue());
        return group;
    }

    public void saveMember(DuplicateMember member) {
        jdbc.update(
            "INSERT INTO duplicate_members (group_id, file_id, confidence) VALUES (?, ?, ?)",
            member.getGroupId(), member.getFileId(), member.getConfidence());
    }

    public List<DuplicateGroup> findByScanId(long scanId) {
        List<DuplicateGroup> groups = jdbc.query(
            "SELECT * FROM duplicate_groups WHERE scan_id = ?",
            groupRowMapper(), scanId);
        groups.forEach(g -> g.setMembers(findMembers(g.getId())));
        return groups;
    }

    public long countByScanId(long scanId) {
        return Objects.requireNonNull(
            jdbc.queryForObject("SELECT COUNT(*) FROM duplicate_groups WHERE scan_id = ?", Long.class, scanId));
    }

    /** Returns groups whose confidence is strictly below {@code threshold} (the review queue). */
    public List<DuplicateGroup> findReviewQueueByScanId(long scanId, double threshold) {
        List<DuplicateGroup> groups = jdbc.query(
            "SELECT * FROM duplicate_groups WHERE scan_id = ? AND confidence < ?",
            groupRowMapper(), scanId, threshold);
        groups.forEach(g -> g.setMembers(findMembers(g.getId())));
        return groups;
    }

    /** Delete a group and all its members (used when the user dismisses a review-queue item). */
    public void deleteById(long groupId) {
        jdbc.update("DELETE FROM duplicate_members WHERE group_id = ?", groupId);
        jdbc.update("DELETE FROM duplicate_groups WHERE id = ?", groupId);
    }

    private List<DuplicateMember> findMembers(long groupId) {
        return jdbc.query(
            "SELECT * FROM duplicate_members WHERE group_id = ?",
            (rs, rowNum) -> new DuplicateMember(
                rs.getLong("group_id"),
                rs.getLong("file_id"),
                rs.getDouble("confidence")),
            groupId);
    }

    private RowMapper<DuplicateGroup> groupRowMapper() {
        return (rs, rowNum) -> {
            DuplicateGroup g = new DuplicateGroup();
            g.setId(rs.getLong("id"));
            g.setScanId(rs.getLong("scan_id"));
            long canonicalId = rs.getLong("canonical_file_id");
            if (!rs.wasNull()) g.setCanonicalFileId(canonicalId);
            g.setStrategy(rs.getString("strategy"));
            g.setConfidence(rs.getDouble("confidence"));
            return g;
        };
    }
}
