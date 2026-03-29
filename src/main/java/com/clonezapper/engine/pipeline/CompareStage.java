package com.clonezapper.engine.pipeline;

import com.clonezapper.db.FileRepository;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.service.HashService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Stage ③: Compare
 * Confirms exact duplicates within each candidate group produced by Stage ②.
 *
 * For each group of file IDs that share size + partial hash:
 *   1. Compute the full file hash for each member
 *   2. Sub-group by full hash — only files with matching full hashes are true duplicates
 *   3. Emit all (A, B) pairs within each full-hash sub-group
 *
 * Confidence is always 1.0 for exact hash matches (strategy = "exact-hash").
 * Files that cannot be read are skipped with a warning; they are not treated as duplicates.
 *
 * Near-duplicate comparison (text similarity, image pHash) is a future extension
 * dispatched via HandlerRegistry.
 */
@Component
public class CompareStage {

    private static final Logger log = LoggerFactory.getLogger(CompareStage.class);

    private final FileRepository fileRepository;
    private final HashService hashService;

    public CompareStage(FileRepository fileRepository, HashService hashService) {
        this.fileRepository = fileRepository;
        this.hashService = hashService;
    }

    /**
     * Confirms which candidate groups contain true exact duplicates.
     *
     * @param candidateGroups output of {@link CandidateStage#execute}
     * @return confirmed duplicate pairs with similarity score and strategy
     */
    public List<ScoredPair> execute(List<List<Long>> candidateGroups) {
        List<ScoredPair> pairs = new ArrayList<>();

        for (List<Long> group : candidateGroups) {
            // Compute full hash for each file in this candidate group
            Map<String, List<Long>> byFullHash = new LinkedHashMap<>();

            for (Long fileId : group) {
                Optional<ScannedFile> fileOpt = fileRepository.findById(fileId);
                if (fileOpt.isEmpty()) {
                    log.warn("Stage ③ — file ID {} not found in DB, skipping", fileId);
                    continue;
                }
                ScannedFile file = fileOpt.get();
                try {
                    String fullHash = hashService.computeFullHash(Path.of(file.getPath()));
                    byFullHash.computeIfAbsent(fullHash, k -> new ArrayList<>()).add(fileId);
                } catch (IOException e) {
                    log.warn("Stage ③ — could not hash {}: {}", file.getPath(), e.getMessage());
                }
            }

            // Emit all (A, B) pairs within each confirmed full-hash sub-group
            for (List<Long> exactGroup : byFullHash.values()) {
                if (exactGroup.size() < 2) continue;
                for (int i = 0; i < exactGroup.size() - 1; i++) {
                    for (int j = i + 1; j < exactGroup.size(); j++) {
                        pairs.add(new ScoredPair(exactGroup.get(i), exactGroup.get(j), 1.0, "exact-hash"));
                    }
                }
            }
        }

        log.info("Stage ③ — {} confirmed pair(s) from {} candidate group(s)",
            pairs.size(), candidateGroups.size());
        return pairs;
    }

    /** Pair of file IDs with a similarity score in [0, 1]. */
    public record ScoredPair(long fileIdA, long fileIdB, double similarity, String strategy) {}
}
