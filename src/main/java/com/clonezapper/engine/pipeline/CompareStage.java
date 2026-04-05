package com.clonezapper.engine.pipeline;

import com.clonezapper.db.FileRepository;
import com.clonezapper.handler.FileTypeHandler;
import com.clonezapper.handler.GenericHandler;
import com.clonezapper.handler.HandlerRegistry;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.service.HashService;
import com.clonezapper.service.ScanSettings;
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
 * For each group of file IDs that share size + partial hash:
 *   1. Compute the full file hash for each member
 *   2. Sub-group by full hash — only files with matching full hashes are true duplicates
 *   3. Emit all (A, B) pairs within each full-hash sub-group
 * Confidence is always 1.0 for exact hash matches (strategy = "exact-hash").
 * Files that cannot be read are skipped with a warning; they are not treated as duplicates.
 * Near-duplicate comparison (text similarity, image pHash) is a future extension
 * dispatched via HandlerRegistry.
 */
@Component
public class CompareStage {

    private static final Logger log = LoggerFactory.getLogger(CompareStage.class);

    /** Minimum similarity for a near-dup pair to be emitted. */
    public static final double MIN_NEAR_DUP_SIMILARITY = 0.5;

    /**
     * Minimum size ratio for near-dup candidates: smaller/larger must be >= this value.
     * Prevents files of wildly different sizes from being grouped as near-duplicates.
     * E.g. 0.5 means a 100 KB file will only be compared against files between 50 KB–200 KB.
     */
    public static final double MIN_SIZE_RATIO = 0.5;


    private final FileRepository fileRepository;
    private final HashService hashService;
    private final HandlerRegistry handlerRegistry;
    private final ScanSettings scanSettings;

    public CompareStage(FileRepository fileRepository,
                        HashService hashService,
                        HandlerRegistry handlerRegistry,
                        ScanSettings scanSettings) {
        this.fileRepository = fileRepository;
        this.hashService = hashService;
        this.handlerRegistry = handlerRegistry;
        this.scanSettings = scanSettings;
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
                    // Reuse cached hash_full if ScanStage already copied it incrementally
                    String fullHash = file.getHashFull();
                    if (fullHash == null) {
                        fullHash = hashService.computeFullHash(Path.of(file.getPath()));
                        fileRepository.updateHashFull(fileId, fullHash);
                    }
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

    /**
     * Near-duplicate pass: for each MIME type with a content-aware handler,
     * compute fingerprints for all files and emit pairs whose similarity is in
     * [MIN_NEAR_DUP_SIMILARITY, 1.0).
     * Exact matches (similarity == 1.0) are deliberately excluded here because
     * the exact-hash pass already handles them — this avoids double-clustering.
     * Files that produce an empty fingerprint (unreadable / unsupported format)
     * are skipped rather than treated as similar.
     */
    public List<ScoredPair> executeNearDup(long scanRunId) {
        List<ScoredPair> pairs = new ArrayList<>();

        List<String> mimeTypes = fileRepository.findDistinctMimeTypesByScanId(scanRunId);

        for (String mimeType : mimeTypes) {
            FileTypeHandler handler = handlerRegistry.dispatch(mimeType);
            if (handler instanceof GenericHandler) continue; // exact-only, nothing to do

            List<ScannedFile> files = fileRepository.findByScanIdAndMimeType(scanRunId, mimeType);
            if (files.size() < 2) continue;

            // Compute fingerprints — reuse cached minhash_signature when available
            Map<Long, byte[]> fingerprints = new LinkedHashMap<>();
            Map<Long, Long>   fileSizes    = new LinkedHashMap<>();
            for (ScannedFile file : files) {
                try {
                    byte[] fp = file.getMinhashSignature();
                    if (fp == null || fp.length == 0) {
                        fp = handler.computeFingerprint(Path.of(file.getPath()));
                        if (fp.length > 0) {
                            fileRepository.updateMinhashSignature(file.getId(), fp);
                        }
                    }
                    if (fp.length > 0) {
                        fingerprints.put(file.getId(), fp);
                        fileSizes.put(file.getId(), file.getSize());
                    }
                } catch (IOException e) {
                    log.warn("Stage ③ near-dup — could not fingerprint {}: {}",
                        file.getPath(), e.getMessage());
                }
            }

            // Compare all pairs within this MIME-type group
            List<Long> ids = new ArrayList<>(fingerprints.keySet());
            for (int i = 0; i < ids.size() - 1; i++) {
                for (int j = i + 1; j < ids.size(); j++) {
                    long idA = ids.get(i);
                    long idB = ids.get(j);

                    // Skip pairs whose sizes differ by more than MIN_SIZE_RATIO.
                    // Files of very different sizes are not near-duplicates.
                    long sA = fileSizes.get(idA);
                    long sB = fileSizes.get(idB);
                    if (sA > 0 && sB > 0) {
                        double sizeRatio = (double) Math.min(sA, sB) / Math.max(sA, sB);
                        if (sizeRatio < MIN_SIZE_RATIO) continue;
                    }

                    double sim = handler.computeSimilarity(
                        fingerprints.get(idA), fingerprints.get(idB));
                    if (sim >= scanSettings.getMinNearDupSimilarity() && sim < 1.0) {
                        String strategy = mimeType.startsWith("image/")
                            ? "near-dup-image" : "near-dup-document";
                        pairs.add(new ScoredPair(idA, idB, sim, strategy));
                    }
                }
            }
        }

        log.info("Stage ③ near-dup — {} near-dup pair(s) found", pairs.size());
        return pairs;
    }

    /** Pair of file IDs with a similarity score in [0, 1]. */
    public record ScoredPair(long fileIdA, long fileIdB, double similarity, String strategy) {}
}
