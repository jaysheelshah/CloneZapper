package com.clonezapper.model.preview;

/**
 * Aggregate stats for the preview summary bar — shown immediately after a scan completes.
 * All fields are computed from existing DB data; nothing is persisted.
 * <p>
 * {@code archiveFreeBytes} is -1 if the archive root does not exist or cannot be checked.
 */
public record PreviewSummary(
        long   totalFilesScanned,
        int    totalGroups,
        int    exactGroups,       // strategy = "exact-hash"
        int    nearDupGroups,     // strategy starts with "near-dup"
        int    totalDupeCount,    // total non-canonical files across all groups (files to archive)
        long   reclaimableBytes,  // sum of non-canonical member sizes across all groups
        double avgConfidence,
        int    autoQueueCount,    // confidence >= threshold
        int    reviewQueueCount,  // confidence < threshold
        String archiveRoot,
        long   archiveFreeBytes   // free space at destination drive; -1 if unknown
) {}
