package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ScanStage;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — Exact duplicate detection.
 * Files with identical content must produce the same partial hash,
 * making them detectable as duplicate candidates.
 */
@Tag("P1")
class ExactDuplicateDetectionTest extends BaseTest {

    @Autowired
    private ScanStage scanStage;

    @Autowired
    private ScanRepository scanRepository;

    @Autowired
    private FileRepository fileRepository;

    @Test
    void exactDuplicatesSharePartialHash() throws IOException {
        Path original = createFile("docs/report.pdf", TestFixtures.IDENTICAL_CONTENT);
        Path duplicate = createFile("downloads/report.pdf", TestFixtures.IDENTICAL_CONTENT);

        long runId = createScanRun();
        scanStage.execute(runId, List.of(tempDir.toString()));

        List<ScannedFile> files = fileRepository.findByScanId(runId);
        assertThat(files).hasSizeGreaterThanOrEqualTo(2);

        List<String> partialHashes = files.stream()
            .map(ScannedFile::getHashPartial)
            .distinct()
            .toList();

        // Both files have identical content → their partial hashes must match
        assertThat(partialHashes).hasSize(1);
    }

    @Test
    void nonDuplicatesHaveDifferentPartialHashes() throws IOException {
        createFile("a/unique1.txt", TestFixtures.CONTENT_A);
        createFile("b/unique2.txt", TestFixtures.CONTENT_B);

        long runId = createScanRun();
        scanStage.execute(runId, List.of(tempDir.toString()));

        List<ScannedFile> files = fileRepository.findByScanId(runId);
        assertThat(files).hasSizeGreaterThanOrEqualTo(2);

        List<String> partialHashes = files.stream()
            .map(ScannedFile::getHashPartial)
            .distinct()
            .toList();

        assertThat(partialHashes).hasSizeGreaterThanOrEqualTo(2);
    }

    private long createScanRun() {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("test-run");
        return scanRepository.save(run).getId();
    }
}
