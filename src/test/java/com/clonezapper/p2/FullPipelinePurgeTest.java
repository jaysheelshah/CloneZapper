package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.ActionRepository;
import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.engine.pipeline.ExecuteStage;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E: scan → stage → purge (permanent delete).
 */
@Tag("P2")
class FullPipelinePurgeTest extends BaseTest {

    @TempDir
    Path archiveDir;

    @Autowired UnifiedScanner scanner;
    @Autowired ExecuteStage executeStage;
    @Autowired DuplicateGroupRepository groupRepository;
    @Autowired ActionRepository actionRepository;

    @Test
    void purgeDeletesArchiveAndMarksActions() throws IOException {
        createFile("a/report.pdf", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/report.pdf", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(groupRepository.countByScanId(run.getId())).isEqualTo(1);

        executeStage.execute(run.getId(), archiveDir.toString());
        assertThat(actionRepository.countByScanId(run.getId())).isEqualTo(1);

        // Verify something is in the archive before purge
        long archivedFiles;
        try (var stream = Files.walk(archiveDir)) {
            archivedFiles = stream.filter(Files::isRegularFile).count();
        }
        assertThat(archivedFiles).isEqualTo(1);

        // Purge
        executeStage.purge(run.getId());

        // Archive directory is empty (or gone)
        long remaining;
        if (Files.exists(archiveDir)) {
            try (var stream = Files.walk(archiveDir)) {
                remaining = stream.filter(Files::isRegularFile).count();
            }
        } else {
            remaining = 0;
        }
        assertThat(remaining).isZero();

        // Actions are marked purged
        actionRepository.findByScanId(run.getId())
            .forEach(a -> assertThat(a.isPurged()).isTrue());
    }

    @Test
    void multipleRunsEachPurgedIndependently() throws IOException {
        // Run 1
        createFile("run1/a.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("run1/b.dat", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run1 = scanner.startScan(List.of(tempDir.resolve("run1").toString()));
        executeStage.execute(run1.getId(), archiveDir.toString());

        // Run 2
        createFile("run2/c.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("run2/d.dat", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run2 = scanner.startScan(List.of(tempDir.resolve("run2").toString()));
        executeStage.execute(run2.getId(), archiveDir.toString());

        // Purge only run 1
        executeStage.purge(run1.getId());

        // Run 1 actions purged, run 2 actions not
        actionRepository.findByScanId(run1.getId())
            .forEach(a -> assertThat(a.isPurged()).isTrue());
        actionRepository.findByScanId(run2.getId())
            .forEach(a -> assertThat(a.isPurged()).isFalse());
    }
}
