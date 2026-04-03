package com.clonezapper.model.preview;

import java.util.List;

/**
 * One row in the preview group table — one per duplicate group.
 * {@code members} is empty until {@link com.clonezapper.service.PreviewService#buildMembers}
 * is called for this group (lazy drill-down on row expand).
 */
public record GroupPreviewRow(
        long   groupId,
        String strategy,          // "exact-hash" | "near-dup-image" | "near-dup-document"
        double confidence,
        int    memberCount,       // all files in the group including canonical
        int    dupeCount,         // memberCount - 1; files that will be archived
        long   reclaimableBytes,  // sum of non-canonical member sizes
        long   totalBytes,        // sum of all member sizes
        String canonicalPath,     // display path of the keeper file
        String canonicalMimeType,
        List<MemberPreviewRow> members  // empty until expanded
) {}
