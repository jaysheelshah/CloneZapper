package com.clonezapper.engine.pipeline;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stage ③: Compare
 * For each candidate pair, runs the appropriate handler (GenericHandler, DocumentHandler,
 * ImageHandler) to compute a precise similarity score.
 *
 * TODO: Implement handler dispatch + full hash / content / pHash comparison.
 */
@Component
public class CompareStage {

    /** @throws UnsupportedOperationException until implemented */
    public List<ScoredPair> execute(List<List<Long>> candidateGroups) {
        throw new UnsupportedOperationException(
            "CompareStage not yet implemented — requires handler dispatch integration");
    }

    /** Pair of file IDs with a similarity score in [0, 1]. */
    public record ScoredPair(long fileIdA, long fileIdB, double similarity, String strategy) {}
}
