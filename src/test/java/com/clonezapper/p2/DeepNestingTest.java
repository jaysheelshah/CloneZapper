package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
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
 * P2 — Deeply nested directories do not cause stack overflow or errors.
 */
@Tag("P2")
class DeepNestingTest extends BaseTest {

    @Autowired private ScanStage scanStage;
    @Autowired private ScanRepository scanRepository;
    @Autowired private FileRepository fileRepository;

    @Test
    void deeplyNestedFilesAreIndexedWithoutError() throws IOException {
        // Build a path 30 levels deep
        String deepPath = "a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/w/x/y/z/1/2/3/4/deep.txt";
        createFile(deepPath, TestFixtures.CONTENT_A);

        long runId = createRun();
        assertThatNoException().isThrownBy(
            () -> scanStage.execute(runId, List.of(tempDir.toString())));
        assertThat(fileRepository.countByScanId(runId)).isEqualTo(1);
    }

    private long createRun() {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("deep-nesting-test");
        return scanRepository.save(run).getId();
    }
}
