package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.ActionRepository;
import com.clonezapper.db.ScanRepository;
import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.engine.pipeline.ExecuteStage;
import com.clonezapper.model.ScanRun;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P2 — ExecuteStage edge cases: cleanup, purge, triple duplicates, missing files.
 */
@Tag("P2")
class ExecuteStageEdgeCasesTest extends BaseTest {

    @Autowired private UnifiedScanner scanner;
    @Autowired private ExecuteStage executeStage;
    @Autowired private ActionRepository actionRepository;
    @Autowired private ScanRepository scanRepository;

    @Test
    void cleanupRestoresDuplicateToOriginalLocation() throws IOException {
        createFile("docs/report.txt",      TestFixtures.IDENTICAL_CONTENT);
        createFile("downloads/report.txt", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        String archiveRoot = tempDir.resolve("archive").toString();

        executeStage.execute(run.getId(), archiveRoot);

        // After staging one file is missing — cleanup must restore it
        executeStage.cleanup(run.getId());

        // Both paths must exist again after cleanup
        assertThat(Files.exists(Path.of(
            actionRepository.findByScanId(run.getId()).getFirst().getOriginalPath())))
            .isTrue();
    }

    @Test
    void purgeDeletesArchiveFolder() throws IOException {
        createFile("docs/report.txt",      TestFixtures.IDENTICAL_CONTENT);
        createFile("downloads/report.txt", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        String archiveRoot = tempDir.resolve("archive").toString();

        executeStage.execute(run.getId(), archiveRoot);

        Path runArchive = Path.of(archiveRoot, "run_" + run.getId());
        assertThat(Files.exists(runArchive)).isTrue();

        executeStage.purge(run.getId());

        assertThat(Files.exists(runArchive)).isFalse();
    }

    @Test
    void tripleGroupStagesTwoDuplicates() throws IOException {
        createFile("dir1/file.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("dir2/file.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("dir3/file.txt", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        String archiveRoot = tempDir.resolve("archive").toString();

        executeStage.execute(run.getId(), archiveRoot);

        // 3 files, 1 canonical kept, 2 moved
        assertThat(actionRepository.countByScanId(run.getId())).isEqualTo(2);

        Path runArchive = Path.of(archiveRoot, "run_" + run.getId());
        long archivedCount;
        try (var stream = Files.walk(runArchive)) {
            archivedCount = stream.filter(Files::isRegularFile).count();
        }
        assertThat(archivedCount).isEqualTo(2);
    }

    @Test
    void archiveRootIsStoredOnScanRun() throws IOException {
        createFile("a/file.txt", TestFixtures.IDENTICAL_CONTENT);
        createFile("b/file.txt", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        String archiveRoot = tempDir.resolve("archive").toString();

        executeStage.execute(run.getId(), archiveRoot);

        ScanRun updated = scanRepository.findById(run.getId()).orElseThrow();
        assertThat(updated.getArchiveRoot()).isEqualTo(archiveRoot);
    }
}
