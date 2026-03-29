package com.clonezapper.engine.pipeline;

import com.clonezapper.db.FileRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Stage ②: Candidates
 * Filters the scanned file set down to candidate duplicate pairs using:
 *   1. Size grouping (files of different sizes cannot be duplicates)
 *   2. Partial hash matching (first 4 KB)
 *   3. MinHash signatures + LSH banding
 *
 * TODO: Implement size grouping + partial hash grouping.
 * TODO: Implement MinHash/LSH candidate generation (requires NearDupService).
 */
@Component
public class CandidateStage {

    private final FileRepository fileRepository;

    public CandidateStage(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    /**
     * Returns groups of file IDs that are candidates for deduplication.
     *
     * @throws UnsupportedOperationException until implemented
     */
    public List<List<Long>> execute(long scanRunId) {
        // Step 1 (available now): size groups
        List<Map<String, Object>> sizeGroups = fileRepository.findSizeGroups(scanRunId, 2);
        if (sizeGroups.isEmpty()) return List.of();

        // TODO: Step 2 — group by hash_partial within each size bucket
        // TODO: Step 3 — MinHash/LSH for near-duplicate candidates
        throw new UnsupportedOperationException(
            "CandidateStage not yet fully implemented — partial hash grouping and LSH pending");
    }
}
