package com.clonezapper.engine.pipeline;

import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.db.FileRepository;
import com.clonezapper.model.DuplicateGroup;
import com.clonezapper.model.DuplicateMember;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.service.ScanSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Stage ④: Cluster
 * Groups confirmed duplicate pairs from Stage ③ into DuplicateGroup records using
 * Union-Find, selects a canonical file per cluster, assigns a confidence score, and
 * routes each group to the Auto Queue (confidence ≥ threshold) or Review Queue.
 * Canonical selection priority (matches DESIGN.md):
 *   1. Oldest modified_at  — the original tends to pre-date its copies
 *   2. Shortest path       — shallower / shorter path is more canonical
 *   3. Lowest file ID      — stable tiebreaker
 * All groups are persisted to the database before being returned.
 */
@Component
public class ClusterStage {

    private static final Logger log = LoggerFactory.getLogger(ClusterStage.class);

    /** Confidence at or above which a group goes to the Auto Queue. */
    public static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.95;

    /**
     * Maximum members a near-dup cluster may have before it is discarded.
     * Clusters above this size are almost always template/boilerplate false positives
     * (e.g. hundreds of HTML files from the same brokerage that share a common page
     * template — MinHash sees high shingle overlap even though each file contains
     * different trade data).  Exact-hash groups are not subject to this cap.
     */
    public static final int MAX_NEAR_DUP_CLUSTER_SIZE = 100;

    private final DuplicateGroupRepository groupRepository;
    private final FileRepository fileRepository;
    private final ScanSettings scanSettings;

    public ClusterStage(DuplicateGroupRepository groupRepository,
                        FileRepository fileRepository,
                        ScanSettings scanSettings) {
        this.groupRepository = groupRepository;
        this.fileRepository = fileRepository;
        this.scanSettings = scanSettings;
    }

    /**
     * Clusters scored pairs into duplicate groups, persists them, and routes by confidence.
     *
     * @param scanRunId the active scan run
     * @param pairs     confirmed pairs from {@link CompareStage#execute}
     * @return auto-queue groups and review-queue groups
     */
    public ClusterResult execute(long scanRunId, List<CompareStage.ScoredPair> pairs) {
        if (pairs.isEmpty()) {
            log.info("Stage ④ — no pairs to cluster");
            return new ClusterResult(List.of(), List.of());
        }

        // ── Union-Find ────────────────────────────────────────────────────────
        Map<Long, Long> parent = new HashMap<>();
        for (CompareStage.ScoredPair pair : pairs) {
            parent.putIfAbsent(pair.fileIdA(), pair.fileIdA());
            parent.putIfAbsent(pair.fileIdB(), pair.fileIdB());
            union(parent, pair.fileIdA(), pair.fileIdB());
        }

        // Group file IDs by their root representative
        Map<Long, Set<Long>> clusters = new LinkedHashMap<>();
        for (Long fileId : parent.keySet()) {
            Long root = find(parent, fileId);
            clusters.computeIfAbsent(root, k -> new LinkedHashSet<>()).add(fileId);
        }

        // ── Build and persist DuplicateGroups ─────────────────────────────────
        List<DuplicateGroup> autoQueue   = new ArrayList<>();
        List<DuplicateGroup> reviewQueue = new ArrayList<>();

        for (Set<Long> cluster : clusters.values()) {
            if (cluster.size() < 2) continue;

            // Determine strategy early — needed to decide whether to prune by size.
            String strategy = pairs.stream()
                .filter(p -> cluster.contains(p.fileIdA()))
                .map(CompareStage.ScoredPair::strategy)
                .findFirst()
                .orElse("unknown");

            // Load all file records for this cluster once.
            Map<Long, ScannedFile> fileMap = new LinkedHashMap<>();
            for (Long id : cluster) {
                fileRepository.findById(id).ifPresent(f -> fileMap.put(id, f));
            }

            // For near-dup clusters, remove members whose size deviates too much from
            // the canonical. The pairwise size filter in CompareStage prevents direct
            // comparison of differently-sized files, but Union-Find transitivity can still
            // chain them (A≈B, B≈C → A and C in same cluster even if size(A)/size(C) << 0.5).
            if (!strategy.startsWith("exact")) {
                Long canonicalId = selectCanonical(fileMap, cluster);
                ScannedFile canonical = fileMap.get(canonicalId);
                if (canonical != null && canonical.getSize() > 0) {
                    final long cSize = canonical.getSize();
                    cluster.removeIf(id -> {
                        if (id.equals(canonicalId)) return false;
                        ScannedFile f = fileMap.get(id);
                        if (f == null) return true;
                        long sz = f.getSize();
                        if (sz <= 0) return true;
                        double ratio = (double) Math.min(cSize, sz) / Math.max(cSize, sz);
                        return ratio < CompareStage.MIN_SIZE_RATIO;
                    });
                    if (cluster.size() < 2) continue;
                }
            }

            // Discard near-dup clusters that grew too large — they are almost always
            // template/boilerplate false positives where MinHash over-groups many files
            // that share a common HTML or document template.
            if (!strategy.startsWith("exact") && cluster.size() > MAX_NEAR_DUP_CLUSTER_SIZE) {
                log.info("Stage ④ — discarding oversized near-dup cluster ({} members, strategy={})",
                    cluster.size(), strategy);
                continue;
            }

            // Confidence = minimum similarity across all retained pairs in this cluster.
            double confidence = pairs.stream()
                .filter(p -> cluster.contains(p.fileIdA()) && cluster.contains(p.fileIdB()))
                .mapToDouble(CompareStage.ScoredPair::similarity)
                .min()
                .orElse(0.0);

            Long canonicalId = selectCanonical(fileMap, cluster);

            DuplicateGroup group = new DuplicateGroup();
            group.setScanId(scanRunId);
            group.setCanonicalFileId(canonicalId);
            group.setStrategy(strategy);
            group.setConfidence(confidence);
            groupRepository.save(group);

            List<DuplicateMember> members = new ArrayList<>();
            for (Long fileId : cluster) {
                DuplicateMember member = new DuplicateMember(group.getId(), fileId, confidence);
                groupRepository.saveMember(member);
                members.add(member);
            }
            group.setMembers(members);

            if (confidence >= scanSettings.getConfidenceThreshold()) {
                autoQueue.add(group);
            } else {
                reviewQueue.add(group);
            }
        }

        log.info("Stage ④ — {} group(s): {} auto-queue, {} review-queue",
            autoQueue.size() + reviewQueue.size(), autoQueue.size(), reviewQueue.size());
        return new ClusterResult(autoQueue, reviewQueue);
    }

    // ── Canonical selection ───────────────────────────────────────────────────

    /** Select canonical from a pre-loaded file map (avoids repeat DB lookups). */
    private Long selectCanonical(Map<Long, ScannedFile> fileMap, Set<Long> fileIds) {
        return fileIds.stream()
            .map(fileMap::get)
            .filter(Objects::nonNull)
            .min(Comparator
                .comparing((ScannedFile f) ->
                    f.getModifiedAt() != null ? f.getModifiedAt() : LocalDateTime.MAX)
                .thenComparingInt(f -> f.getPath().length())
                .thenComparingLong(ScannedFile::getId))
            .map(ScannedFile::getId)
            .orElse(fileIds.iterator().next());
    }

    // ── Union-Find with path compression ─────────────────────────────────────

    private Long find(Map<Long, Long> parent, Long node) {
        if (!parent.get(node).equals(node)) {
            parent.put(node, find(parent, parent.get(node)));
        }
        return parent.get(node);
    }

    private void union(Map<Long, Long> parent, Long x, Long y) {
        Long rootX = find(parent, x);
        Long rootY = find(parent, y);
        if (!rootX.equals(rootY)) {
            parent.put(rootX, rootY);
        }
    }

    // ── Result ────────────────────────────────────────────────────────────────

    public record ClusterResult(
        List<DuplicateGroup> autoQueue,
        List<DuplicateGroup> reviewQueue
    ) {}
}
