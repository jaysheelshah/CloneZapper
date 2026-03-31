package com.clonezapper.p2;

import com.clonezapper.BaseTest;
import com.clonezapper.TestFixtures;
import com.clonezapper.db.DuplicateGroupRepository;
import com.clonezapper.db.FileRepository;
import com.clonezapper.engine.UnifiedScanner;
import com.clonezapper.model.DuplicateGroup;
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
 * E2E: duplicate detection across multiple separately-scanned root directories.
 */
@Tag("P2")
class MultiDirectoryScanE2ETest extends BaseTest {

    @TempDir Path dirB;

    @Autowired UnifiedScanner scanner;
    @Autowired DuplicateGroupRepository groupRepository;
    @Autowired FileRepository fileRepository;

    @Test
    void duplicatesAcrossTwoRootsAreDetected() throws IOException {
        createFile("a/report.pdf", TestFixtures.IDENTICAL_CONTENT);
        // Write the same content into the second independent temp dir
        Path copy = dirB.resolve("b/report.pdf");
        Files.createDirectories(copy.getParent());
        Files.write(copy, TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(
            List.of(tempDir.toString(), dirB.toString()));

        assertThat(run.getPhase()).isEqualTo("COMPLETE");
        assertThat(fileRepository.countByScanId(run.getId())).isEqualTo(2);
        assertThat(groupRepository.countByScanId(run.getId())).isEqualTo(1);
    }

    @Test
    void uniqueFilesAcrossRootsProduceNoGroups() throws IOException {
        createFile("unique_a.txt", TestFixtures.CONTENT_A);
        Path uniq = dirB.resolve("unique_b.txt");
        Files.write(uniq, TestFixtures.CONTENT_B);

        ScanRun run = scanner.startScan(
            List.of(tempDir.toString(), dirB.toString()));

        assertThat(run.getPhase()).isEqualTo("COMPLETE");
        assertThat(groupRepository.countByScanId(run.getId())).isZero();
    }

    @Test
    void duplicatesWithinOneRootAndAcrossRootsGroupedCorrectly() throws IOException {
        // 3 copies of same content: 2 in dirA, 1 in dirB → should all be one group
        createFile("copy1/file.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("copy2/file.dat", TestFixtures.IDENTICAL_CONTENT);
        Path third = dirB.resolve("copy3/file.dat");
        Files.createDirectories(third.getParent());
        Files.write(third, TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(
            List.of(tempDir.toString(), dirB.toString()));

        assertThat(run.getPhase()).isEqualTo("COMPLETE");
        List<DuplicateGroup> groups = groupRepository.findByScanId(run.getId());
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().getMembers()).hasSize(3);
    }

    @Test
    void emptySecondRootScansWithoutError() throws IOException {
        createFile("doc.txt", TestFixtures.CONTENT_A);
        // dirB is empty — just the directory itself

        ScanRun run = scanner.startScan(
            List.of(tempDir.toString(), dirB.toString()));

        assertThat(run.getPhase()).isEqualTo("COMPLETE");
        assertThat(fileRepository.countByScanId(run.getId())).isEqualTo(1);
        assertThat(groupRepository.countByScanId(run.getId())).isZero();
    }
}
