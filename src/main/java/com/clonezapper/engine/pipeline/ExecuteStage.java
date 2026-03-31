package com.clonezapper.engine.pipeline;

import com.clonezapper.db.ActionRepository;
import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.model.Action;
import com.clonezapper.model.DuplicateGroup;
import com.clonezapper.model.DuplicateMember;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.model.ScanRun;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage ⑤: Execute
 * Two-phase safe-delete model:
 *   execute  — moves non-canonical duplicates to a mirrored archive folder (reversible)
 *   cleanup  — restores all files from archive back to their original paths (full undo)
 *   purge    — permanently deletes the archive for a run (point of no return)
 * Archive structure:
 *   {archiveRoot}/run_{scanId}/{drive}/{original/path/mirrored}
 * Every file move is recorded as an Action row so cleanup/purge have a full audit trail.
 */
@Component
public class ExecuteStage {

    private static final Logger log = LoggerFactory.getLogger(ExecuteStage.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private final FileRepository fileRepository;
    private final DuplicateGroupRepository groupRepository;
    private final ActionRepository actionRepository;
    private final ScanRepository scanRepository;

    public ExecuteStage(FileRepository fileRepository,
                        DuplicateGroupRepository groupRepository,
                        ActionRepository actionRepository,
                        ScanRepository scanRepository) {
        this.fileRepository = fileRepository;
        this.groupRepository = groupRepository;
        this.actionRepository = actionRepository;
        this.scanRepository = scanRepository;
    }

    /**
     * Moves all non-canonical duplicate files to the archive. Canonical files are untouched.
     * Each move is recorded in the actions table so it can be reversed via {@link #cleanup}.
     *
     * @param scanRunId   the scan run whose duplicate groups to stage
     * @param archiveRoot root folder for all archives (e.g. C:\CloneZapperArchive)
     */
    public void execute(long scanRunId, String archiveRoot) {
        List<DuplicateGroup> groups = groupRepository.findByScanId(scanRunId);
        if (groups.isEmpty()) {
            log.info("Stage ⑤ — no duplicate groups to stage for run {}", scanRunId);
            return;
        }

        scanRepository.updateArchiveRoot(scanRunId, archiveRoot);
        int moved = 0;

        for (DuplicateGroup group : groups) {
            for (DuplicateMember member : group.getMembers()) {
                if (member.getFileId().equals(group.getCanonicalFileId())) continue;

                ScannedFile file = fileRepository.findById(member.getFileId()).orElse(null);
                if (file == null) continue;

                Path source = Path.of(file.getPath());
                if (!Files.exists(source)) {
                    log.warn("Stage ⑤ — source file not found, skipping: {}", file.getPath());
                    continue;
                }

                Path destination = buildArchivePath(archiveRoot, scanRunId, file.getPath());
                try {
                    Files.createDirectories(destination.getParent());
                    Files.move(source, destination);

                    Action action = new Action();
                    action.setScanId(scanRunId);
                    action.setFileId(file.getId());
                    action.setActionType(Action.Type.MOVE);
                    action.setOriginalPath(file.getPath());
                    action.setDestination(destination.toString());
                    action.setExecutedAt(LocalDateTime.now());
                    actionRepository.save(action);

                    moved++;
                    log.debug("Staged: {} → {}", file.getPath(), destination);
                } catch (IOException e) {
                    log.error("Stage ⑤ — failed to move {}: {}", file.getPath(), e.getMessage());
                }
            }
        }

        log.info("Stage ⑤ execute complete — {} file(s) staged for run {}", moved, scanRunId);

        // Write machine-readable + human-readable reports into the archive folder
        scanRepository.findById(scanRunId).ifPresent(run -> writeReports(run, groups, archiveRoot));
    }

    /**
     * Returns a description of the files that would be staged without moving anything.
     * Used by {@code --dry-run} in the CLI.
     *
     * @return list of strings: "SOURCE → DESTINATION" for each non-canonical duplicate
     */
    public List<String> preview(long scanRunId, String archiveRoot) {
        List<DuplicateGroup> groups = groupRepository.findByScanId(scanRunId);
        List<String> lines = new ArrayList<>();
        for (DuplicateGroup group : groups) {
            for (DuplicateMember member : group.getMembers()) {
                if (member.getFileId().equals(group.getCanonicalFileId())) continue;
                ScannedFile file = fileRepository.findById(member.getFileId()).orElse(null);
                if (file == null) continue;
                Path dest = buildArchivePath(archiveRoot, scanRunId, file.getPath());
                lines.add(file.getPath() + "  →  " + dest);
            }
        }
        return lines;
    }

    /**
     * Restores all staged files back to their original locations (undoes {@link #execute}).
     * Safe — will not overwrite a file that has reappeared at the original path.
     *
     * @param scanRunId the scan run to restore
     */
    public void cleanup(long scanRunId) {
        List<Action> actions = actionRepository.findByScanId(scanRunId).stream()
            .filter(a -> !a.isCleaned() && !a.isPurged() && a.getActionType() == Action.Type.MOVE)
            .toList();

        int restored = 0;
        for (Action action : actions) {
            Path archive  = Path.of(action.getDestination());
            Path original = Path.of(action.getOriginalPath());

            if (!Files.exists(archive)) {
                log.warn("cleanup — archive file not found, skipping: {}", archive);
                continue;
            }
            if (Files.exists(original)) {
                log.warn("cleanup — original path already occupied, skipping: {}", original);
                continue;
            }

            try {
                Files.createDirectories(original.getParent());
                Files.move(archive, original);
                restored++;
                log.debug("Restored: {} → {}", archive, original);
            } catch (IOException e) {
                log.error("cleanup — failed to restore {}: {}", archive, e.getMessage());
            }
        }

        actionRepository.markCleaned(scanRunId);
        log.info("cleanup complete — {} file(s) restored for run {}", restored, scanRunId);
    }

    /**
     * Permanently deletes the archive folder for a run. Cannot be undone.
     * Marks all actions for the run as purged.
     *
     * @param scanRunId the scan run to purge
     */
    public void purge(long scanRunId) {
        scanRepository.findById(scanRunId).ifPresent(run -> {
            if (run.getArchiveRoot() != null) {
                Path runArchive = Path.of(run.getArchiveRoot(), "run_" + scanRunId);
                try {
                    deleteRecursively(runArchive);
                    log.info("purge — deleted archive: {}", runArchive);
                } catch (IOException e) {
                    log.error("purge — failed to delete archive {}: {}", runArchive, e.getMessage());
                }
            }
        });
        actionRepository.markPurged(scanRunId);
        log.info("purge complete for run {}", scanRunId);
    }

    // ── Archive reports ───────────────────────────────────────────────────────

    /**
     * Writes {@code _clonezapper_report.json} and {@code _clonezapper_report.html}
     * into the run's archive folder. Both are re-importable / human-readable summaries.
     */
    private void writeReports(ScanRun run, List<DuplicateGroup> groups, String archiveRoot) {
        Path runDir = Path.of(archiveRoot, "run_" + run.getId());
        try {
            Files.createDirectories(runDir);
        } catch (IOException e) {
            log.error("writeReports — could not create run dir {}: {}", runDir, e.getMessage());
            return;
        }

        List<ScannedFile> files = fileRepository.findByScanId(run.getId());
        List<Action> actions = actionRepository.findByScanId(run.getId());

        // ── JSON ──────────────────────────────────────────────────────────────
        try {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("formatVersion", "1");
            report.put("runId", run.getId());
            report.put("runLabel", run.getRunLabel());
            report.put("phase", run.getPhase());
            report.put("createdAt", run.getCreatedAt() != null ? run.getCreatedAt().toString() : null);
            report.put("completedAt", run.getCompletedAt() != null ? run.getCompletedAt().toString() : null);
            report.put("archiveRoot", archiveRoot);

            List<Map<String, Object>> filesJson = new ArrayList<>();
            for (ScannedFile f : files) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", f.getId());
                row.put("path", f.getPath());
                row.put("size", f.getSize());
                row.put("mimeType", f.getMimeType());
                row.put("hashFull", f.getHashFull());
                row.put("copyHint", f.getCopyHint());
                filesJson.add(row);
            }
            report.put("files", filesJson);

            List<Map<String, Object>> groupsJson = new ArrayList<>();
            for (DuplicateGroup g : groups) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", g.getId());
                row.put("strategy", g.getStrategy());
                row.put("confidence", g.getConfidence());
                row.put("canonicalFileId", g.getCanonicalFileId());
                List<Map<String, Object>> members = new ArrayList<>();
                for (DuplicateMember m : g.getMembers()) {
                    members.add(Map.of("fileId", m.getFileId(), "confidence", m.getConfidence()));
                }
                row.put("members", members);
                groupsJson.add(row);
            }
            report.put("duplicateGroups", groupsJson);

            List<Map<String, Object>> actionsJson = new ArrayList<>();
            for (Action a : actions) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", a.getId());
                row.put("fileId", a.getFileId());
                row.put("actionType", a.getActionType().name());
                row.put("originalPath", a.getOriginalPath());
                row.put("destination", a.getDestination());
                row.put("executedAt", a.getExecutedAt() != null ? a.getExecutedAt().toString() : null);
                actionsJson.add(row);
            }
            report.put("actions", actionsJson);

            MAPPER.writeValue(runDir.resolve("_clonezapper_report.json").toFile(), report);
            log.info("writeReports — JSON written to {}", runDir);
        } catch (IOException e) {
            log.error("writeReports — failed to write JSON report: {}", e.getMessage());
        }

        // ── HTML ──────────────────────────────────────────────────────────────
        try {
            long staged = actions.stream().filter(a -> a.getActionType() == Action.Type.MOVE).count();
            long recoverableBytes = groups.stream()
                .flatMap(g -> g.getMembers().stream()
                    .filter(m -> !m.getFileId().equals(g.getCanonicalFileId()))
                    .flatMap(m -> files.stream().filter(f -> f.getId().equals(m.getFileId()))))
                .mapToLong(ScannedFile::getSize).sum();

            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
                .append("<title>CloneZapper Report — ").append(run.getRunLabel()).append("</title>")
                .append("<style>body{font-family:monospace;padding:2em;max-width:900px;margin:auto}")
                .append("h1{color:#333}table{border-collapse:collapse;width:100%}")
                .append("th,td{border:1px solid #ccc;padding:6px 10px;text-align:left}")
                .append("th{background:#f5f5f5}.keep{color:green}.dup{color:#c00}</style></head><body>");
            html.append("<h1>CloneZapper — Session Report</h1>");
            html.append("<p><strong>Run:</strong> ").append(run.getRunLabel())
                .append(" &nbsp; <strong>Phase:</strong> ").append(run.getPhase()).append("</p>");
            if (run.getCreatedAt() != null)
                html.append("<p><strong>Started:</strong> ").append(run.getCreatedAt()).append("</p>");
            html.append("<p><strong>Files scanned:</strong> ").append(String.format("%,d", files.size())).append("</p>");
            html.append("<p><strong>Duplicate groups:</strong> ").append(groups.size())
                .append(" &nbsp; <strong>Recoverable:</strong> ").append(formatBytes(recoverableBytes)).append("</p>");
            html.append("<p><strong>Staged (moved):</strong> ").append(staged).append(" file(s)</p>");
            html.append("<h2>Duplicate Groups</h2><table><tr><th>#</th><th>Strategy</th>")
                .append("<th>Confidence</th><th>Role</th><th>Path</th><th>Size</th></tr>");
            int idx = 1;
            for (DuplicateGroup g : groups) {
                for (DuplicateMember m : g.getMembers()) {
                    boolean isCanonical = m.getFileId().equals(g.getCanonicalFileId());
                    ScannedFile f = files.stream().filter(sf -> sf.getId().equals(m.getFileId()))
                        .findFirst().orElse(null);
                    String path = f != null ? f.getPath() : "?";
                    String size = f != null ? formatBytes(f.getSize()) : "?";
                    String role = isCanonical ? "<span class=\"keep\">★ keep</span>"
                                              : "<span class=\"dup\">duplicate</span>";
                    html.append("<tr><td>").append(idx).append("</td><td>").append(g.getStrategy())
                        .append("</td><td>").append(String.format("%.0f%%", g.getConfidence() * 100))
                        .append("</td><td>").append(role).append("</td><td>").append(path)
                        .append("</td><td>").append(size).append("</td></tr>");
                }
                idx++;
            }
            html.append("</table></body></html>");
            Files.writeString(runDir.resolve("_clonezapper_report.html"), html.toString());
            log.info("writeReports — HTML written to {}", runDir);
        } catch (IOException e) {
            log.error("writeReports — failed to write HTML report: {}", e.getMessage());
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // ── Archive path construction ─────────────────────────────────────────────

    /**
     * Maps an original file path to its mirrored position under the archive root.
     * Windows: C:\Users\foo\file.txt → {archive}/run_{id}/C/Users/foo/file.txt
     * Unix:    /home/foo/file.txt    → {archive}/run_{id}/home/foo/file.txt
     */
    static Path buildArchivePath(String archiveRoot, long scanRunId, String originalPath) {
        Path original = Path.of(originalPath);
        String root = original.getRoot() != null ? original.getRoot().toString() : "";
        // Strip drive colon and separators → "C:\" becomes "C", "/" becomes ""
        String drivePrefix = root.replaceAll("[:/\\\\]", "").trim();
        String pathFromRoot = originalPath.substring(root.length());

        return drivePrefix.isEmpty()
            ? Path.of(archiveRoot, "run_" + scanRunId).resolve(pathFromRoot)
            : Path.of(archiveRoot, "run_" + scanRunId, drivePrefix).resolve(pathFromRoot);
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        if (Files.isDirectory(path)) {
            try (var entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.delete(path);
    }
}
