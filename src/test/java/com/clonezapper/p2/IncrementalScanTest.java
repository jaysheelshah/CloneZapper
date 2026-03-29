package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.FileRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.pipeline.ScanStage;
import com.clonezapper.model.ScannedFile;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

/**
 * P2 — Incremental scan reuses fingerprints for unchanged files.
 * TODO: Implement incremental logic in ScanStage (check existing hash by path + size + modified_at).
 */
@Tag("P2")
class IncrementalScanTest extends BaseTest {

    @Autowired private ScanStage scanStage;
    @Autowired private ScanRepository scanRepository;
    @Autowired private FileRepository fileRepository;

    @Test
    @Disabled("TODO: implement incremental scan — reuse existing fingerprints when file unchanged")
    void unchangedFileIsNotRehashedOnSecondScan() throws Exception {
        createFile("stable.txt", TestFixtures.CONTENT_A);

        long run1 = createRun("run1");
        List<ScannedFile> firstScan = scanStage.execute(run1, List.of(tempDir.toString()));
        String hashAfterFirstScan = firstScan.get(0).getHashPartial();

        // Simulate second scan — file unchanged
        long run2 = createRun("run2");
        List<ScannedFile> secondScan = scanStage.execute(run2, List.of(tempDir.toString()));
        String hashAfterSecondScan = secondScan.get(0).getHashPartial();

        // TODO: assert that second scan reused existing hash without re-reading the file
        // (verify via a spy/mock on HashService that computePartialHash was NOT called)
    }

    private long createRun(String label) {
        ScanRun run = new ScanRun();
        run.setPhase("SCANNING");
        run.setCreatedAt(LocalDateTime.now());
        run.setRunLabel(label);
        return scanRepository.save(run).getId();
    }
}
