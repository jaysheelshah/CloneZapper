package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ScanStage;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P1 — Canonical file is never modified during scan.
 * Stage ① must be purely read-only. No file should be moved, renamed, or altered.
 */
@Tag("P1")
class CanonicalFileUntouchedTest extends BaseTest {

    @Autowired
    private ScanStage scanStage;

    @Autowired
    private ScanRepository scanRepository;

    @Test
    void scanDoesNotModifyAnyFile() throws IOException {
        Path file1 = createFile("important/document.pdf", TestFixtures.IDENTICAL_CONTENT);
        Path file2 = createFile("backup/document.pdf", TestFixtures.IDENTICAL_CONTENT);

        long sizeBefore1 = Files.size(file1);
        long sizeBefore2 = Files.size(file2);
        long modifiedBefore1 = Files.getLastModifiedTime(file1).toMillis();
        long modifiedBefore2 = Files.getLastModifiedTime(file2).toMillis();
        byte[] contentBefore1 = Files.readAllBytes(file1);
        byte[] contentBefore2 = Files.readAllBytes(file2);

        long runId = createScanRun();
        scanStage.execute(runId, List.of(tempDir.toString()));

        // Files must be completely untouched
        assertThat(Files.size(file1)).isEqualTo(sizeBefore1);
        assertThat(Files.size(file2)).isEqualTo(sizeBefore2);
        assertThat(Files.getLastModifiedTime(file1).toMillis()).isEqualTo(modifiedBefore1);
        assertThat(Files.getLastModifiedTime(file2).toMillis()).isEqualTo(modifiedBefore2);
        assertThat(Files.readAllBytes(file1)).isEqualTo(contentBefore1);
        assertThat(Files.readAllBytes(file2)).isEqualTo(contentBefore2);
        assertThat(Files.exists(file1)).isTrue();
        assertThat(Files.exists(file2)).isTrue();
    }

    private long createScanRun() {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("canonical-test");
        return scanRepository.save(run).getId();
    }
}
