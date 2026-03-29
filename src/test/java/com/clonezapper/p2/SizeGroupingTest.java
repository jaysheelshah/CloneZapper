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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 — Size grouping correctly identifies files that share a size.
 * Files with different sizes are eliminated immediately (cannot be duplicates).
 */
@Tag("P2")
class SizeGroupingTest extends BaseTest {

    @Autowired private ScanStage scanStage;
    @Autowired private ScanRepository scanRepository;
    @Autowired private FileRepository fileRepository;

    @Test
    void filesWithDifferentSizesFormNoSizeGroups() throws IOException {
        createFile("a.txt", "short");
        createFile("b.txt", "this is longer content");
        createFile("c.txt", "this is even longer content here");

        long runId = createRun();
        scanStage.execute(runId, List.of(tempDir.toString()));

        List<Map<String, Object>> groups = fileRepository.findSizeGroups(runId, 2);
        assertThat(groups).isEmpty();
    }

    @Test
    void filesWithSameSizeFormASizeGroup() throws IOException {
        // Exact same byte count — pad carefully
        String content = "exactly-twenty-bytes";  // 20 bytes
        createFile("x/file1.txt", content);
        createFile("y/file2.txt", content);
        createFile("z/file3.txt", content);

        long runId = createRun();
        scanStage.execute(runId, List.of(tempDir.toString()));

        List<Map<String, Object>> groups = fileRepository.findSizeGroups(runId, 2);
        assertThat(groups).hasSize(1);
        assertThat(((Number) groups.get(0).get("cnt")).intValue()).isEqualTo(3);
    }

    private long createRun() {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel("size-grouping-test");
        return scanRepository.save(run).getId();
    }
}
