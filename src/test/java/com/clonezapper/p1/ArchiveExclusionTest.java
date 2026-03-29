package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ScanStage;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.model.ScanRun;
import com.clonezapper.provider.LocalFilesystemProvider;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — Archive root is excluded from scans.
 * Files inside the archive folder must never appear as scan candidates.
 * Scanning the archive would cause re-deduplication of already-staged files.
 *
 * NOTE: Full archive exclusion is enforced at the UnifiedScanner level (not yet implemented).
 * This test verifies that the LocalFilesystemProvider correctly enumerates only the
 * requested path, and that files outside the requested path are not included.
 */
@Tag("P1")
class ArchiveExclusionTest extends BaseTest {

    @Autowired
    private LocalFilesystemProvider provider;

    @Autowired
    private ScanStage scanStage;

    @Autowired
    private ScanRepository scanRepository;

    @Autowired
    private FileRepository fileRepository;

    @Test
    void scanOnlyEnumeratesFilesUnderRequestedPath() throws IOException {
        // Source directory
        Path sourceDir = createDir("source");
        createFile("source/doc.txt", TestFixtures.CONTENT_A);
        createFile("source/image.jpg", TestFixtures.CONTENT_B);

        // Archive directory (sibling of source — must NOT appear in scan results)
        createFile("archive/run_001/doc.txt", TestFixtures.CONTENT_A);

        long runId = createScanRun();
        scanStage.execute(runId, List.of(sourceDir.toString()));

        List<ScannedFile> files = fileRepository.findByScanId(runId);

        // Only source files should be indexed
        assertThat(files).hasSize(2);
        assertThat(files).allMatch(f -> f.getPath().contains("source"));
        assertThat(files).noneMatch(f -> f.getPath().contains("archive"));
    }

    @Test
    void providerEnumeratesOnlyFilesUnderGivenRoot() throws IOException {
        createFile("target/a.txt", TestFixtures.CONTENT_A);
        createFile("other/b.txt", TestFixtures.CONTENT_B);

        Path targetDir = tempDir.resolve("target");
        long count = provider.enumerate(targetDir.toString()).count();
        assertThat(count).isEqualTo(1);
    }

    private long createScanRun() {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("exclusion-test");
        return scanRepository.save(run).getId();
    }
}
