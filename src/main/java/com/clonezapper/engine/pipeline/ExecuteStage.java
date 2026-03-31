package com.clonezapper.engine.pipeline;

import com.clonezapper.db.ActionRepository;
import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.model.Action;
import com.clonezapper.model.DuplicateGroup;
import com.clonezapper.model.DuplicateMember;
import com.clonezapper.model.ScannedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

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
