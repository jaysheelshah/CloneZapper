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
import java.util.ArrayList;
import java.util.List;

/**
 * Stage ①: Scan
 * Enumerates files from local paths, computes partial hashes, detects copy patterns,
 * and persists each file record to the database.
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

    /**
     * Scans all given root paths and stores file records under the given scanRunId.
     *
     * @param scanRunId  the ID of the active ScanRun
     * @param rootPaths  local filesystem paths to scan
     * @return           list of persisted ScannedFile records
     */
    public List<ScannedFile> execute(long scanRunId, List<String> rootPaths) {
        List<ScannedFile> results = new ArrayList<>();

        for (String rootPath : rootPaths) {
            log.info("Scanning path: {}", rootPath);
            try (var stream = provider.enumerate(rootPath)) {
                stream.forEach(path -> {
                    try {
                        ScannedFile file = processFile(scanRunId, path);
                        fileRepository.save(file);
                        results.add(file);
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

    private ScannedFile processFile(long scanRunId, Path path) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
        String mimeType = probeMimeType(path);
        String partialHash = hashService.computePartialHash(path);
        String copyHint = CopyPatternDetector.detect(path.getFileName().toString());

        return ScannedFile.builder()
            .scanId(scanRunId)
            .path(path.toAbsolutePath().toString())
            .provider("local")
            .size(attrs.size())
            .modifiedAt(LocalDateTime.ofInstant(
                attrs.lastModifiedTime().toInstant(), ZoneId.systemDefault()))
            .mimeType(mimeType)
            .hashPartial(partialHash)
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
