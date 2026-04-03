package com.clonezapper.model.preview;

import java.time.LocalDateTime;

/**
 * One row in the per-group drill-down — one per file in a duplicate group.
 * {@code proposedArchivePath} is null for the canonical (keeper) file
 * and the computed mirror path for all other members.
 */
public record MemberPreviewRow(
        long          fileId,
        String        path,
        long          sizeBytes,
        LocalDateTime modifiedAt,
        String        mimeType,
        double        confidence,
        boolean       isCanonical,
        String        proposedArchivePath  // null if canonical; mirror path if duplicate
) {}
