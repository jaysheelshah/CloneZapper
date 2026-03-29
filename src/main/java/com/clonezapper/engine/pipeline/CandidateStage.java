package com.clonezapper.engine.pipeline;

import com.clonezapper.db.FileRepository;
import com.clonezapper.model.ScannedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stage ②: Candidates
 * Filters the scanned file set down to candidate duplicate groups using:
 *   1. Size grouping  — files of different sizes cannot be duplicates
 *   2. Partial hash grouping — first 4 KB hash narrows to likely-identical content
 *
 * Near-duplicate detection (MinHash + LSH) is a future extension; this stage
 * handles exact-duplicate candidates only.
 *
 * Returns groups of ≥ 2 file IDs that share both size and partial hash.
 * Each group is a set of files that must be confirmed by Stage ③ (full hash).
 */
@Component
public class CandidateStage {

    private static final Logger log = LoggerFactory.getLogger(CandidateStage.class);

    private final FileRepository fileRepository;

    public CandidateStage(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    /**
     * Returns groups of file IDs that are candidates for exact deduplication.
     * Files with null partial hashes (e.g. unreadable during scan) are skipped.
     *
     * @param scanRunId the active scan run
     * @return list of candidate groups; each group has ≥ 2 file IDs
     */
    public List<List<Long>> execute(long scanRunId) {
        List<Map<String, Object>> sizeGroups = fileRepository.findSizeGroups(scanRunId, 2);
        if (sizeGroups.isEmpty()) {
            log.info("Stage ② — no size groups found, no candidates");
            return List.of();
        }

        List<List<Long>> candidateGroups = new ArrayList<>();

        for (Map<String, Object> sizeGroup : sizeGroups) {
            long size = ((Number) sizeGroup.get("size")).longValue();
            List<ScannedFile> files = fileRepository.findByScanIdAndSize(scanRunId, size);

            // Group file IDs by partial hash
            Map<String, List<Long>> byPartialHash = new LinkedHashMap<>();
            for (ScannedFile file : files) {
                String hash = file.getHashPartial();
                if (hash != null) {
                    byPartialHash.computeIfAbsent(hash, k -> new ArrayList<>()).add(file.getId());
                }
            }

            // Only groups with 2+ files are candidates
            for (List<Long> group : byPartialHash.values()) {
                if (group.size() >= 2) {
                    candidateGroups.add(group);
                }
            }
        }

        log.info("Stage ② — {} candidate group(s) from {} size bucket(s)",
            candidateGroups.size(), sizeGroups.size());
        return candidateGroups;
    }
}
