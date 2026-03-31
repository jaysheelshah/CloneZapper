package com.clonezapper.p1;

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
 * E2E: scan → stage → cleanup (full reversible delete cycle).
 * Uses a separate @TempDir for the archive so it is outside the scan root.
 */
@Tag("P1")
class FullPipelineWithStagingTest extends BaseTest {

    @TempDir
    Path archiveDir;

    @Autowired UnifiedScanner scanner;
    @Autowired ExecuteStage executeStage;
    @Autowired DuplicateGroupRepository groupRepository;
    @Autowired ActionRepository actionRepository;

    @Test
    void stageMovesDuplicateAndLeavesCanonical() throws IOException {
        Path canonical  = createFile("originals/report.pdf", TestFixtures.IDENTICAL_CONTENT);
        Path duplicate  = createFile("downloads/report.pdf", TestFixtures.IDENTICAL_CONTENT);
        Path unique     = createFile("unique.txt",           TestFixtures.CONTENT_A);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(groupRepository.countByScanId(run.getId())).isEqualTo(1);

        // Stage — moves the non-canonical copy to the archive
        executeStage.execute(run.getId(), archiveDir.toString());

        // Exactly one file was moved
        assertThat(actionRepository.countByScanId(run.getId())).isEqualTo(1);

        // One of the two identical files must still exist at its original location
        boolean canonicalStillExists = Files.exists(canonical) || Files.exists(duplicate);
        assertThat(canonicalStillExists).isTrue();

        // The unique file is untouched
        assertThat(Files.exists(unique)).isTrue();

        // Archive directory is non-empty
        long archivedCount;
        try (var stream = Files.walk(archiveDir)) {
            archivedCount = stream.filter(Files::isRegularFile).count();
        }
        assertThat(archivedCount).isEqualTo(1);
    }

    @Test
    void cleanupRestoresDuplicateToOriginalLocation() throws IOException {
        createFile("copies/report.pdf", TestFixtures.IDENTICAL_CONTENT);
        createFile("originals/report.pdf", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        executeStage.execute(run.getId(), archiveDir.toString());

        // Confirm something was staged
        assertThat(actionRepository.countByScanId(run.getId())).isEqualTo(1);

        // Record which paths currently exist before cleanup
        long filesBefore;
        try (var stream = Files.walk(tempDir)) {
            filesBefore = stream.filter(Files::isRegularFile).count();
        }

        // Cleanup — restores the staged file
        executeStage.cleanup(run.getId());

        long filesAfter;
        try (var stream = Files.walk(tempDir)) {
            filesAfter = stream.filter(Files::isRegularFile).count();
        }

        // Both original files are present again
        assertThat(filesAfter).isEqualTo(filesBefore + 1);
    }
}
