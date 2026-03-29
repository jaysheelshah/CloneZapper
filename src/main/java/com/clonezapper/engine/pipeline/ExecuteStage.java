package com.clonezapper.engine.pipeline;

import com.clonezapper.model.DuplicateGroup;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stage ⑤: Execute
 * Moves confirmed duplicate files to the run archive folder (mirrored hierarchy).
 * Records each action in the database with the original path for cleanup/purge.
 *
 * Two-phase model:
 *   stage   → moves duplicates to archive (this stage)
 *   cleanup → restores files to original paths
 *   purge   → permanently deletes the archive
 *
 * TODO: Implement file move + mirrored-path archive construction.
 * TODO: Implement cleanup (restore) and purge operations.
 */
@Component
public class ExecuteStage {

    /** @throws UnsupportedOperationException until implemented */
    public void execute(long scanRunId, List<DuplicateGroup> groups, String archiveRoot) {
        throw new UnsupportedOperationException(
            "ExecuteStage not yet implemented — requires archive move logic");
    }

    /** @throws UnsupportedOperationException until implemented */
    public void cleanup(long scanRunId) {
        throw new UnsupportedOperationException(
            "ExecuteStage.cleanup not yet implemented — requires restore-from-archive logic");
    }

    /** @throws UnsupportedOperationException until implemented */
    public void purge(long scanRunId) {
        throw new UnsupportedOperationException(
            "ExecuteStage.purge not yet implemented — requires permanent delete logic");
    }
}
