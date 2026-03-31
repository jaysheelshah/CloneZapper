package com.clonezapper.p1;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.ActionRepository;
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
 * P1 — ExecuteStage correctness.
 * Canonical files must be untouched; duplicates must be moved to archive; actions recorded.
 */
@Tag("P1")
class ExecuteStageTest extends BaseTest {

    @Autowired private UnifiedScanner scanner;
    @Autowired private ExecuteStage executeStage;
    @Autowired private ActionRepository actionRepository;

    @Test
    void canonicalFileIsUntouchedAfterStaging() throws IOException {
        Path original  = createFile("docs/report.txt",      TestFixtures.IDENTICAL_CONTENT);
        Path duplicate = createFile("downloads/report.txt", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        String archiveRoot = tempDir.resolve("archive").toString();

        executeStage.execute(run.getId(), archiveRoot);

        // Exactly one of the two files must still exist (the canonical)
        boolean originalExists  = Files.exists(original);
        boolean duplicateExists = Files.exists(duplicate);
        assertThat(originalExists || duplicateExists)
            .as("canonical file must still exist").isTrue();
        assertThat(originalExists && duplicateExists)
            .as("duplicate must have been moved").isFalse();
    }

    @Test
    void duplicateIsMovedToArchive() throws IOException {
        createFile("docs/report.txt",      TestFixtures.IDENTICAL_CONTENT);
        createFile("downloads/report.txt", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        String archiveRoot = tempDir.resolve("archive").toString();

        executeStage.execute(run.getId(), archiveRoot);

        // Archive folder must exist and contain at least one file
        Path runArchive = Path.of(archiveRoot, "run_" + run.getId());
        assertThat(Files.exists(runArchive)).isTrue();
        long archivedCount;
        try (var stream = Files.walk(runArchive)) {
            archivedCount = stream.filter(Files::isRegularFile).count();
        }
        assertThat(archivedCount).isEqualTo(1);
    }

    @Test
    void actionIsRecordedForEachMovedFile() throws IOException {
        createFile("docs/report.txt",      TestFixtures.IDENTICAL_CONTENT);
        createFile("downloads/report.txt", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        String archiveRoot = tempDir.resolve("archive").toString();

        executeStage.execute(run.getId(), archiveRoot);

        assertThat(actionRepository.countByScanId(run.getId())).isEqualTo(1);
        var actions = actionRepository.findByScanId(run.getId());
        assertThat(actions.getFirst().getOriginalPath()).isNotBlank();
        assertThat(actions.getFirst().getDestination()).contains("run_" + run.getId());
    }

    @Test
    void noDuplicatesResultsInNoActions() throws IOException {
        createFile("a.txt", TestFixtures.CONTENT_A);
        createFile("b.txt", TestFixtures.CONTENT_B);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        String archiveRoot = tempDir.resolve("archive").toString();

        executeStage.execute(run.getId(), archiveRoot);

        assertThat(actionRepository.countByScanId(run.getId())).isZero();
    }
}
