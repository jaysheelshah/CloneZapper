package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ScanStage;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * P2 — Empty directories are handled gracefully.
 */
@Tag("P2")
class EmptyDirectoryTest extends BaseTest {

    @Autowired private ScanStage scanStage;
    @Autowired private ScanRepository scanRepository;
    @Autowired private FileRepository fileRepository;

    @Test
    void scanOfEmptyDirectoryProducesNoFiles() throws IOException {
        createDir("empty");
        long runId = createRun();
        assertThatNoException().isThrownBy(
            () -> scanStage.execute(runId, List.of(tempDir.resolve("empty").toString())));
        assertThat(fileRepository.countByScanId(runId)).isEqualTo(0);
    }

    @Test
    void scanOfDirectoryWithOnlySubdirsProducesNoFiles() throws IOException {
        createDir("a/b/c");
        long runId = createRun();
        assertThatNoException().isThrownBy(
            () -> scanStage.execute(runId, List.of(tempDir.resolve("a").toString())));
        assertThat(fileRepository.countByScanId(runId)).isEqualTo(0);
    }

    private long createRun() {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("empty-dir-test");
        return scanRepository.save(run).getId();
    }
}
