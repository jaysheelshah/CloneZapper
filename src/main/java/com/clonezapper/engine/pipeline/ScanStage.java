package com.clonezapper.engine.pipeline;

import com.clonezapper.db.FileRepository;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.provider.LocalFilesystemProvider;
import com.clonezapper.service.CopyPatternDetector;
import com.clonezapper.service.HashService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * Stage ①: Scan
 * Enumerates files from local paths, computes partial hashes, detects copy patterns,
 * and persists each file record to the database.
 * Supports:
 *  - Path exclusion: any path under an excluded root is silently skipped.
 *  - Incremental hashing: if a previous scan record exists for the same path with
 *    matching size and modification time, hashPartial / hashFull / minhashSignature
 *    are reused without re-reading the file.
 *  - Batch path cache: previous fingerprints are loaded in one bulk query at the
 *    start of the scan instead of one query per file.
 *  - Batch INSERT: file records are flushed to the database in chunks of
 *    {@value BATCH_SIZE} to minimise SQLite transaction overhead.
 */
@Component
public class ScanStage {

    private static final Logger log = LoggerFactory.getLogger(ScanStage.class);

    /** Number of file records inserted per SQLite transaction. */
    private static final int BATCH_SIZE = 500;

    private final LocalFilesystemProvider provider;
    private final HashService hashService;
    private final FileRepository fileRepository;

    public ScanStage(LocalFilesystemProvider provider,
                     HashService hashService,
                     FileRepository fileRepository) {
        this.provider = provider;
        this.hashService = hashService;
        this.fileRepository = fileRepository;
    }

    /** Scan without exclusions — delegates to the full overload. */
    public List<ScannedFile> execute(long scanRunId, List<String> rootPaths) {
        return execute(scanRunId, rootPaths, Set.of());
    }

    /**
     * Scan all given root paths, skipping any file whose absolute path starts
     * with one of the {@code excludePaths} entries.
     */
    public List<ScannedFile> execute(long scanRunId, List<String> rootPaths,
                                     Set<String> excludePaths) {
        return execute(scanRunId, rootPaths, excludePaths, count -> {});
    }

    /**
     * Same as {@link #execute(long, List, Set)} but fires {@code onFileIndexed}
     * with the running total after every successfully indexed file.
     * <p>
     * Performance notes:
     * <ul>
     *   <li>All previously known path fingerprints are loaded in one bulk query at
     *       the start (batch path cache), eliminating one DB round-trip per file.</li>
     *   <li>Scanned file records are written to the DB in batches of {@value BATCH_SIZE},
     *       reducing the number of SQLite transactions from N to N/{@value BATCH_SIZE}.</li>
     * </ul>
     */
    public List<ScannedFile> execute(long scanRunId, List<String> rootPaths,
                                     Set<String> excludePaths, IntConsumer onFileIndexed) {
        Set<Path> excluded = excludePaths.stream()
            .map(p -> Path.of(p).toAbsolutePath().normalize())
            .collect(Collectors.toSet());

        // Load all previously known path fingerprints in one query (replaces per-file lookups).
        Map<String, ScannedFile> pathCache = fileRepository.loadLatestByPath();
        log.debug("Batch path cache loaded — {} known paths", pathCache.size());

        List<ScannedFile> results  = new ArrayList<>();
        List<ScannedFile> pending  = new ArrayList<>(BATCH_SIZE); // unflushed records

        for (String rootPath : rootPaths) {
            log.info("Scanning path: {}", rootPath);
            try (var stream = provider.enumerate(rootPath)) {
                stream.forEach(path -> {
                    if (isExcluded(path, excluded)) {
                        log.debug("Skipping (excluded): {}", path);
                        return;
                    }
                    try {
                        ScannedFile file = processFile(scanRunId, path, pathCache);
                        if (file.getSize() == 0) {
                            log.debug("Skipping zero-byte file: {}", path);
                            return;
                        }
                        results.add(file);
                        pending.add(file);
                        onFileIndexed.accept(results.size());
                        log.debug("Scanned: {} ({} bytes)", path, file.getSize());

                        if (pending.size() >= BATCH_SIZE) {
                            fileRepository.saveBatch(pending);
                            pending.clear();
                        }
                    } catch (IOException e) {
                        log.warn("Skipping {}: {}", path, e.getMessage());
                    }
                });
            } catch (IOException e) {
                log.error("Failed to enumerate {}: {}", rootPath, e.getMessage());
            }
        }

        // Flush the final partial batch.
        if (!pending.isEmpty()) {
            fileRepository.saveBatch(pending);
        }

        log.info("Scan complete — {} files indexed", results.size());
        return results;
    }

    /**
     * Fast filesystem walk that collects a lightweight {@code path → "size|modifiedAt"}
     * snapshot without computing any hashes or touching the database.
     * <p>
     * Used by {@link com.clonezapper.engine.UnifiedScanner} as a cheap pre-check:
     * if this snapshot matches the previous scan's snapshot the full pipeline can be
     * skipped entirely.
     */
    public Map<String, String> quickSnapshot(List<String> rootPaths, Set<String> excludePaths) {
        Set<Path> excluded = excludePaths.stream()
            .map(p -> Path.of(p).toAbsolutePath().normalize())
            .collect(Collectors.toSet());

        Map<String, String> snapshot = new HashMap<>();

        for (String rootPath : rootPaths) {
            try (var stream = provider.enumerate(rootPath)) {
                stream.forEach(path -> {
                    if (isExcluded(path, excluded)) return;
                    try {
                        BasicFileAttributes attrs =
                            Files.readAttributes(path, BasicFileAttributes.class);
                        if (attrs.size() == 0) return;
                        LocalDateTime mod = LocalDateTime
                            .ofInstant(attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault())
                            .truncatedTo(ChronoUnit.SECONDS);
                        snapshot.put(path.toAbsolutePath().toString(),
                            attrs.size() + "|" + mod);
                    } catch (IOException ignored) {}
                });
            } catch (IOException e) {
                log.warn("quickSnapshot: failed to enumerate {}: {}", rootPath, e.getMessage());
            }
        }

        return snapshot;
    }

    // ── internals ────────────────────────────────────────────────────────────

    private boolean isExcluded(Path path, Set<Path> excluded) {
        Path normalised = path.toAbsolutePath().normalize();
        return excluded.stream().anyMatch(normalised::startsWith);
    }

    /**
     * Build a ScannedFile for {@code path}, reusing fingerprints from the batch
     * path cache when size + modifiedAt are unchanged.
     */
    private ScannedFile processFile(long scanRunId, Path path,
                                    Map<String, ScannedFile> pathCache) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        long size = attrs.size();
        LocalDateTime modifiedAt = LocalDateTime.ofInstant(
            attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());

        String partialHash = null;
        String fullHash    = null;
        byte[] minhash     = null;

        ScannedFile prev = pathCache.get(path.toAbsolutePath().toString());
        if (prev != null && prev.getHashPartial() != null) {
            boolean sameSize = prev.getSize() == size;
            boolean sameMod  = prev.getModifiedAt() != null
                && modifiedAt.truncatedTo(ChronoUnit.SECONDS)
                             .equals(prev.getModifiedAt().truncatedTo(ChronoUnit.SECONDS));
            if (sameSize && sameMod) {
                partialHash = prev.getHashPartial();
                fullHash    = prev.getHashFull();
                minhash     = prev.getMinhashSignature();
                log.debug("Incremental: reused fingerprints for {}", path.getFileName());
            }
        }
        if (partialHash == null) {
            partialHash = hashService.computePartialHash(path);
        }

        String mimeType  = probeMimeType(path);
        String copyHint  = CopyPatternDetector.detect(path.getFileName().toString());

        return ScannedFile.builder()
            .scanId(scanRunId)
            .path(path.toAbsolutePath().toString())
            .provider("local")
            .size(size)
            .modifiedAt(modifiedAt)
            .mimeType(mimeType)
            .hashPartial(partialHash)
            .hashFull(fullHash)
            .minhashSignature(minhash)
            .copyHint(copyHint)
            .build();
    }

    private String probeMimeType(Path path) {
        try {
            String type = Files.probeContentType(path);
            return type != null ? type : "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }
}
