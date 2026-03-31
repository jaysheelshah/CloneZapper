package com.clonezapper.service;

import org.springframework.stereotype.Service;

/**
 * Mutable runtime settings for the deduplication engine.
 * Values are held in-memory and reset on restart.
 * The confidence threshold controls the Auto Queue / Review Queue split in Stage ④.
 */
@Service
public class ScanSettings {

    /**
     * Groups at or above this confidence go straight to the Auto Queue;
     * below it they appear in the Review Queue for manual inspection.
     * Default matches {@code ClusterStage.DEFAULT_CONFIDENCE_THRESHOLD}.
     */
    private volatile double confidenceThreshold = 0.95;

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }

    /** @throws IllegalArgumentException if {@code threshold} is not in [0.0, 1.0] */
    public void setConfidenceThreshold(double threshold) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException("Confidence threshold must be between 0.0 and 1.0");
        }
        this.confidenceThreshold = threshold;
    }
}
