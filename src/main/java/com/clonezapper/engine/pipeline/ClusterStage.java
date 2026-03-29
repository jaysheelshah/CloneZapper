package com.clonezapper.engine.pipeline;

import com.clonezapper.model.DuplicateGroup;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Stage ④: Cluster
 * Groups scored pairs into duplicate clusters using Union-Find, selects the canonical
 * file per cluster, assigns confidence scores, and routes to Auto Queue vs Review Queue.
 *
 * TODO: Implement Union-Find clustering + canonical selection + confidence-based routing.
 */
@Component
public class ClusterStage {

    /** Confidence threshold above which groups go to the Auto Queue. */
    public static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.95;

    /** @throws UnsupportedOperationException until implemented */
    public ClusterResult execute(long scanRunId, List<CompareStage.ScoredPair> pairs) {
        throw new UnsupportedOperationException(
            "ClusterStage not yet implemented — requires Union-Find + canonical selection");
    }

    public record ClusterResult(
        List<DuplicateGroup> autoQueue,    // confidence >= threshold
        List<DuplicateGroup> reviewQueue   // confidence < threshold
    ) {}
}
