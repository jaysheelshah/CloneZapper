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
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E2E edge-case coverage: empty dir, single file, many identical files,
 * deep nesting, multiple independent scan runs.
 */
@Tag("P2")
class ScanEdgeCasesE2ETest extends BaseTest {

    @Autowired UnifiedScanner scanner;
    @Autowired DuplicateGroupRepository groupRepository;
    @Autowired FileRepository fileRepository;

    @Test
    void emptyDirectoryCompletesWithNoFiles() {
        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");
        assertThat(fileRepository.countByScanId(run.getId())).isZero();
        assertThat(groupRepository.countByScanId(run.getId())).isZero();
    }

    @Test
    void singleFileProducesNoGroups() throws IOException {
        createFile("only.txt", TestFixtures.CONTENT_A);
        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");
        assertThat(fileRepository.countByScanId(run.getId())).isEqualTo(1);
        assertThat(groupRepository.countByScanId(run.getId())).isZero();
    }

    @Test
    void fiveIdenticalFilesFormOneGroupWithFiveMembers() throws IOException {
        for (int i = 1; i <= 5; i++) {
            createFile("copy" + i + "/file.dat", TestFixtures.IDENTICAL_CONTENT);
        }
        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");

        List<DuplicateGroup> groups = groupRepository.findByScanId(run.getId());
        assertThat(groups).hasSize(1);
        assertThat(groups.getFirst().getMembers()).hasSize(5);
        assertThat(groups.getFirst().getCanonicalFileId()).isNotNull();
    }

    @Test
    void twoPairsOfDuplicatesFormTwoGroups() throws IOException {
        createFile("pair1/a.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("pair1/b.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("pair2/a.dat", TestFixtures.CONTENT_A);
        createFile("pair2/b.dat", TestFixtures.CONTENT_A);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");

        assertThat(groupRepository.countByScanId(run.getId())).isEqualTo(2);
    }

    @Test
    void duplicatesInDeeplyNestedDirectoriesAreFound() throws IOException {
        createFile("a/b/c/d/e/deep.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("x/y/z/also_deep.dat", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");
        assertThat(groupRepository.countByScanId(run.getId())).isEqualTo(1);
    }

    @Test
    void twoIndependentRunsDoNotShareGroups() throws IOException {
        createFile("run1/a.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("run1/b.dat", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run1 = scanner.startScan(List.of(tempDir.resolve("run1").toString()));
        assertThat(groupRepository.countByScanId(run1.getId())).isEqualTo(1);

        createFile("run2/c.dat", TestFixtures.CONTENT_A);
        createFile("run2/d.dat", TestFixtures.CONTENT_A);

        ScanRun run2 = scanner.startScan(List.of(tempDir.resolve("run2").toString()));
        assertThat(groupRepository.countByScanId(run2.getId())).isEqualTo(1);

        // Groups from run1 are not visible in run2 query and vice versa
        assertThat(groupRepository.countByScanId(run1.getId())).isEqualTo(1);
        assertThat(groupRepository.countByScanId(run2.getId())).isEqualTo(1);
    }

    @Test
    void allFilesUniqueProducesNoGroupsEvenWithManyFiles() throws IOException {
        for (int i = 0; i < 10; i++) {
            createFile("file" + i + ".txt", "unique content number " + i + " padding " + "x".repeat(100));
        }
        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        assertThat(run.getPhase()).isEqualTo("COMPLETE");
        assertThat(fileRepository.countByScanId(run.getId())).isEqualTo(10);
        assertThat(groupRepository.countByScanId(run.getId())).isZero();
    }

    @Test
    void canonicalFileIsAlwaysAMemberOfItsGroup() throws IOException {
        createFile("a.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("b.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("c.dat", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        List<DuplicateGroup> groups = groupRepository.findByScanId(run.getId());
        assertThat(groups).hasSize(1);

        DuplicateGroup group = groups.getFirst();
        boolean canonicalIsMember = group.getMembers().stream()
            .anyMatch(m -> m.getFileId().equals(group.getCanonicalFileId()));
        assertThat(canonicalIsMember).isTrue();
    }

    @Test
    void scanPhaseProgressionIsCorrect() throws IOException {
        createFile("a.dat", TestFixtures.IDENTICAL_CONTENT);
        createFile("b.dat", TestFixtures.IDENTICAL_CONTENT);

        ScanRun run = scanner.startScan(List.of(tempDir.toString()));
        // Final phase must be COMPLETE (not FAILED, not SCANNING, etc.)
        assertThat(run.getPhase()).isEqualTo("COMPLETE");
    }
}
