package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ScanStage;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — False positive prevention.
 * Files with different content must NOT be grouped as duplicate candidates.
 * This is the most safety-critical test — false positives lead to data loss.
 */
@Tag("P1")
class FalsePositivePreventionTest extends BaseTest {

    @Autowired
    private ScanStage scanStage;

    @Autowired
    private ScanRepository scanRepository;

    @Autowired
    private FileRepository fileRepository;

    @Test
    void filesWithDifferentContentAreNotCandidates() throws IOException {
        // Create 5 files with completely different content and different sizes
        for (int i = 0; i < 5; i++) {
            createFile("file" + i + ".txt", "unique content number " + i + " padding " + "x".repeat(i * 10));
        }

        long runId = createScanRun();
        scanStage.execute(runId, List.of(tempDir.toString()));

        // No two files should share a partial hash (all content is unique)
        List<Map<String, Object>> sizeGroups = fileRepository.findSizeGroups(runId, 2);
        // Even if sizes accidentally collide, partial hashes must differ
        List<ScannedFile> files = fileRepository.findByScanId(runId);
        long distinctHashes = files.stream()
            .map(ScannedFile::getHashPartial)
            .distinct()
            .count();

        assertThat(distinctHashes).isEqualTo(files.size());
    }

    @Test
    void sameFilenamesDifferentContentAreNotCandidates() throws IOException {
        // Same filename in different dirs but completely different content — must NOT match
        createFile("docs/2023/report.txt", "Annual report 2023 — content one");
        createFile("docs/2024/report.txt", "Annual report 2024 — completely different content here");

        long runId = createScanRun();
        scanStage.execute(runId, List.of(tempDir.toString()));

        List<ScannedFile> files = fileRepository.findByScanId(runId);
        List<String> hashes = files.stream().map(ScannedFile::getHashPartial).distinct().toList();
        assertThat(hashes).hasSize(2);
    }

    private long createScanRun() {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("fp-test-run");
        return scanRepository.save(run).getId();
    }
}
