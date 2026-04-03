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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * Stage ①: Scan
 * Enumerates files from local paths, computes partial hashes, detects copy patterns,
 * and persists each file record to the database.
 * Supports:
 *  - Path exclusion: any path under an excluded root is silently skipped (used to
 *    prevent the archive directory from being re-scanned as duplicates).
 *  - Incremental hashing: if a previous scan record exists for the same path with
 *    matching size and modification time, hashPartial / hashFull / minhashSignature
 *    are reused without re-reading the file.
 */
@Component
public class ScanStage {

    private static final Logger log = LoggerFactory.getLogger(ScanStage.class);

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
     *
     * @param scanRunId    the ID of the active ScanRun
     * @param rootPaths    local filesystem paths to scan
     * @param excludePaths paths to skip entirely (e.g. the archive root)
     * @return             list of persisted ScannedFile records
     */
    public List<ScannedFile> execute(long scanRunId, List<String> rootPaths,
                                     Set<String> excludePaths) {
        return execute(scanRunId, rootPaths, excludePaths, count -> {});
    }

    /**
     * Same as {@link #execute(long, List, Set)} but fires {@code onFileIndexed}
     * with the running total after every successfully indexed file.
     * Used by {@link com.clonezapper.engine.UnifiedScanner} to drive live progress.
     */
    public List<ScannedFile> execute(long scanRunId, List<String> rootPaths,
                                     Set<String> excludePaths, IntConsumer onFileIndexed) {
        // Normalise exclusion paths once up-front
        Set<Path> excluded = excludePaths.stream()
            .map(p -> Path.of(p).toAbsolutePath().normalize())
            .collect(Collectors.toSet());

        List<ScannedFile> results = new ArrayList<>();

        for (String rootPath : rootPaths) {
            log.info("Scanning path: {}", rootPath);
            try (var stream = provider.enumerate(rootPath)) {
                stream.forEach(path -> {
                    if (isExcluded(path, excluded)) {
                        log.debug("Skipping (excluded): {}", path);
                        return;
                    }
                    try {
                        ScannedFile file = processFile(scanRunId, path);
                        fileRepository.save(file);
                        results.add(file);
                        onFileIndexed.accept(results.size());
                        log.debug("Scanned: {} ({} bytes)", path, file.getSize());
                    } catch (IOException e) {
                        log.warn("Skipping {}: {}", path, e.getMessage());
                    }
                });
            } catch (IOException e) {
                log.error("Failed to enumerate {}: {}", rootPath, e.getMessage());
            }
        }

        log.info("Scan complete — {} files indexed", results.size());
        return results;
    }

    // ── internals ────────────────────────────────────────────────────────────

    private boolean isExcluded(Path path, Set<Path> excluded) {
        Path normalised = path.toAbsolutePath().normalize();
        return excluded.stream().anyMatch(normalised::startsWith);
    }

    private ScannedFile processFile(long scanRunId, Path path) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        long size = attrs.size();
        LocalDateTime modifiedAt = LocalDateTime.ofInstant(
            attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault());

        // Incremental: reuse all stored fingerprints if size + modified_at match
        String partialHash  = null;
        String fullHash     = null;
        byte[] minhash      = null;

        Optional<ScannedFile> prev = fileRepository.findLatestByPath(
            path.toAbsolutePath().toString());
        if (prev.isPresent() && prev.get().getHashPartial() != null) {
            ScannedFile p = prev.get();
            boolean sameSize = p.getSize() == size;
            boolean sameMod  = p.getModifiedAt() != null
                && modifiedAt.truncatedTo(ChronoUnit.SECONDS)
                             .equals(p.getModifiedAt().truncatedTo(ChronoUnit.SECONDS));
            if (sameSize && sameMod) {
                partialHash = p.getHashPartial();
                fullHash    = p.getHashFull();           // may be null — computed later
                minhash     = p.getMinhashSignature();   // may be null — computed later
                log.debug("Incremental: reused fingerprints for {}", path.getFileName());
            }
        }
        if (partialHash == null) {
            partialHash = hashService.computePartialHash(path);
        }

        String mimeType = probeMimeType(path);
        String copyHint = CopyPatternDetector.detect(path.getFileName().toString());

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
